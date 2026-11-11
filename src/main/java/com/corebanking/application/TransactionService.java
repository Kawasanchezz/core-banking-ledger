package com.corebanking.application;

import com.corebanking.api.dto.TransactionDtos.AmountRequest;
import com.corebanking.api.dto.TransactionDtos.TransactionResponse;
import com.corebanking.api.dto.TransactionDtos.TransferRequest;
import com.corebanking.domain.Account;
import com.corebanking.domain.AccountType;
import com.corebanking.domain.BankTransaction;
import com.corebanking.domain.Money;
import com.corebanking.domain.TransactionType;
import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accounts;
    private final AccountService accountService;
    private final LedgerPoster ledger;
    private final IdempotencyService idempotency;

    public TransactionService(AccountRepository accounts, AccountService accountService,
                              LedgerPoster ledger, IdempotencyService idempotency) {
        this.accounts = accounts;
        this.accountService = accountService;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Transactional
    public IdempotentResult<TransactionResponse> deposit(Long customerId, Long accountId, String idemKey,
                                                         AmountRequest req) {
        IdempotencyService.validateKey(idemKey);
        BigDecimal amount = Money.positive(req.amount());
        String hash = IdempotencyService.fingerprint("DEPOSIT", accountId, amount, req.description());

        return idempotency.execute(customerId, idemKey, hash, TransactionResponse.class, () -> {
            accountService.assertOwned(customerId, accountId);
            Map<Long, Account> locked = lockInOrder(Account.CASH_ACCOUNT_ID, accountId);
            Account cash = locked.get(Account.CASH_ACCOUNT_ID);
            Account target = locked.get(accountId);

            BankTransaction tx = ledger.post(TransactionType.DEPOSIT, amount, req.description(),
                    cash, target, customerId, idemKey);
            log.info("Depósito efetuado tx={} conta={}", tx.getId(), accountId);
            return response(tx, null, accountId, target.getBalance());
        });
    }

    @Transactional
    public IdempotentResult<TransactionResponse> withdraw(Long customerId, Long accountId, String idemKey,
                                                          AmountRequest req) {
        IdempotencyService.validateKey(idemKey);
        BigDecimal amount = Money.positive(req.amount());
        String hash = IdempotencyService.fingerprint("WITHDRAWAL", accountId, amount, req.description());

        return idempotency.execute(customerId, idemKey, hash, TransactionResponse.class, () -> {
            accountService.assertOwned(customerId, accountId);
            Map<Long, Account> locked = lockInOrder(Account.CASH_ACCOUNT_ID, accountId);
            Account cash = locked.get(Account.CASH_ACCOUNT_ID);
            Account source = locked.get(accountId);

            BankTransaction tx = ledger.post(TransactionType.WITHDRAWAL, amount, req.description(),
                    source, cash, customerId, idemKey);
            log.info("Saque efetuado tx={} conta={}", tx.getId(), accountId);
            return response(tx, accountId, null, source.getBalance());
        });
    }

    @Transactional
    public IdempotentResult<TransactionResponse> transfer(Long customerId, String idemKey, TransferRequest req) {
        IdempotencyService.validateKey(idemKey);
        BigDecimal amount = Money.positive(req.amount());
        Long sourceId = req.sourceAccountId();
        Long targetId = req.targetAccountId();
        if (sourceId.equals(targetId)) {
            throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER,
                    "Origem e destino não podem ser a mesma conta");
        }
        String hash = IdempotencyService.fingerprint("TRANSFER", sourceId, targetId, amount, req.description());

        return idempotency.execute(customerId, idemKey, hash, TransactionResponse.class, () -> {
            accountService.assertOwned(customerId, sourceId);
            Map<Long, Account> locked = lockInOrder(sourceId, targetId);
            Account source = locked.get(sourceId);
            Account target = locked.get(targetId);
            if (target.getType() == AccountType.CAIXA) {
                throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "Conta de destino não encontrada");
            }

            BankTransaction tx = ledger.post(TransactionType.TRANSFER, amount, req.description(),
                    source, target, customerId, idemKey);
            log.info("Transferência efetuada tx={} origem={} destino={}", tx.getId(), sourceId, targetId);
            return response(tx, sourceId, targetId, source.getBalance());
        });
    }

    /**
     * Bloqueia (FOR UPDATE) as contas SEMPRE em ordem crescente de id. Duas transferências cruzadas
     * (A→B e B→A) disputam os locks na mesma ordem e portanto nunca entram em deadlock.
     */
    private Map<Long, Account> lockInOrder(Long... ids) {
        Map<Long, Account> locked = new HashMap<>();
        for (Long id : new TreeSet<>(java.util.List.of(ids))) {
            Account a = accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta " + id + " não encontrada"));
            locked.put(id, a);
        }
        return locked;
    }

    private static TransactionResponse response(BankTransaction tx, Long source, Long target, BigDecimal balanceAfter) {
        return new TransactionResponse(tx.getId(), tx.getType(), tx.getAmount(), tx.getDescription(),
                source, target, balanceAfter, tx.getCreatedAt());
    }
}
