package com.corebanking.integration;

import com.corebanking.api.dto.TransactionDtos.AmountRequest;
import com.corebanking.api.dto.TransactionDtos.TransactionResponse;
import com.corebanking.api.dto.TransactionDtos.TransferRequest;
import com.corebanking.application.IdempotentResult;
import com.corebanking.application.ReconciliationService;
import com.corebanking.domain.Account;
import com.corebanking.domain.AccountType;
import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReconciliationService reconciliation;

    @Test
    void concurrentCrossTransfersNeverGoNegativeNorDeadlockAndPreserveTotals() throws Exception {
        int n = 6;
        BigDecimal initial = new BigDecimal("1000.00");
        List<Long> customers = new ArrayList<>();
        List<Long> accs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Long c = newCustomer();
            Long a = newAccount(c, i % 2 == 0 ? AccountType.CORRENTE : AccountType.POUPANCA);
            deposit(c, a, initial.toPlainString());
            customers.add(c);
            accs.add(a);
        }

        int threads = 8;
        int operations = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < operations; t++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    int from = r.nextInt(n);
                    int to = (from + 1 + r.nextInt(n - 1)) % n;
                    BigDecimal amount = BigDecimal.valueOf(r.nextInt(1, 40_000), 2).add(new BigDecimal("100.00"));
                    transactionService.transfer(customers.get(from), UUID.randomUUID().toString(),
                            new TransferRequest(accs.get(from), accs.get(to), amount, "concorrente"));
                    ok.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getCode() == ErrorCode.INSUFFICIENT_FUNDS) {
                        insufficient.incrementAndGet();
                    } else {
                        unexpected.add(e);
                    }
                } catch (Throwable e) {
                    unexpected.add(e);
                }
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unexpected).as("nenhum deadlock/lock timeout/erro inesperado").isEmpty();
        assertThat(ok.get()).isGreaterThan(0);

        BigDecimal total = BigDecimal.ZERO;
        for (Long id : accs) {
            Account a = accounts.findById(id).orElseThrow();
            assertThat(a.getBalance()).as("conta %s sem saldo negativo", id).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            total = total.add(a.getBalance());
        }
        assertThat(total).isEqualByComparingTo(initial.multiply(BigDecimal.valueOf(n)));

        BigDecimal systemSum = jdbc.queryForObject("SELECT COALESCE(SUM(balance), 0) FROM accounts", BigDecimal.class);
        assertThat(systemSum).isEqualByComparingTo("0");
        assertThat(reconciliation.findInconsistencies()).isEmpty();
    }

    @Test
    void concurrentDebitsOnSameAccountCannotOverdraw() throws Exception {
        Long c = newCustomer();
        Long acc = newAccount(c, AccountType.POUPANCA);
        deposit(c, acc, "100.00");

        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                try {
                    transactionService.withdraw(c, acc, UUID.randomUUID().toString(),
                            new AmountRequest(new BigDecimal("30.00"), "saque"));
                    success.incrementAndGet();
                } catch (BusinessException e) {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(3);
        assertThat(accounts.findById(acc).orElseThrow().getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void concurrentRequestsWithSameIdempotencyKeyApplyOnlyOnce() throws Exception {
        Long c = newCustomer();
        Long acc = newAccount(c, AccountType.CORRENTE);
        String key = "same-key-" + UUID.randomUUID();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IdempotentResult<TransactionResponse>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return transactionService.deposit(c, acc, key, new AmountRequest(new BigDecimal("50.00"), "retry storm"));
            }));
        }
        start.countDown();

        List<IdempotentResult<TransactionResponse>> results = new ArrayList<>();
        for (var f : futures) {
            results.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(results).extracting(r -> r.body().transactionId()).containsOnly(results.get(0).body().transactionId());
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(results.stream().filter(IdempotentResult::replayed).count()).isEqualTo(threads - 1);
        assertThat(accounts.findById(acc).orElseThrow().getBalance()).isEqualByComparingTo("50.00");
        Integer entries = jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id = ?", Integer.class, acc);
        assertThat(entries).isEqualTo(1);
    }
}
