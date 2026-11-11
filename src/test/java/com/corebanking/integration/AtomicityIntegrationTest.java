package com.corebanking.integration;

import com.corebanking.api.dto.TransactionDtos.TransferRequest;
import com.corebanking.application.ReconciliationService;
import com.corebanking.domain.Account;
import com.corebanking.domain.AccountType;
import com.corebanking.infrastructure.repository.AccountRepository;
import com.corebanking.infrastructure.repository.BankTransactionRepository;
import com.corebanking.infrastructure.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomicityIntegrationTest extends AbstractIntegrationTest {

    @Autowired LedgerEntryRepository ledgerEntries;
    @Autowired AccountRepository accounts;
    @Autowired BankTransactionRepository transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReconciliationService reconciliation;

    @Test
    void failureOnCreditEntryRollsBackDebitBalancesTransactionAndIdempotencyKey() {
        Long c1 = newCustomer();
        Long c2 = newCustomer();
        Long from = newAccount(c1, AccountType.CORRENTE);
        Long to = newAccount(c2, AccountType.CORRENTE);
        deposit(c1, from, "100.00");

        long txBefore = transactions.count();
        long entriesBefore = ledgerEntries.count();
        String key = "atomic-" + UUID.randomUUID();

        jdbc.execute("CREATE OR REPLACE FUNCTION test_fail_credit() RETURNS trigger AS $$ BEGIN "
                + "RAISE EXCEPTION 'falha simulada no credito'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER trg_test_fail_credit BEFORE INSERT ON ledger_entries FOR EACH ROW "
                + "WHEN (NEW.direction = 'CREDIT' AND NEW.account_id = " + to + ") EXECUTE FUNCTION test_fail_credit()");
        try {
            assertThatThrownBy(() -> transactionService.transfer(c1, key,
                    new TransferRequest(from, to, new BigDecimal("60.00"), "vai falhar")))
                    .hasMessageContaining("falha simulada");
        } finally {
            jdbc.execute("DROP TRIGGER trg_test_fail_credit ON ledger_entries");
        }

        Account src = accounts.findById(from).orElseThrow();
        Account dst = accounts.findById(to).orElseThrow();
        assertThat(src.getBalance()).isEqualByComparingTo("100.00");
        assertThat(dst.getBalance()).isEqualByComparingTo("0.00");
        assertThat(transactions.count()).isEqualTo(txBefore);
        assertThat(ledgerEntries.count()).isEqualTo(entriesBefore);
        Integer keys = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE idem_key = ?",
                Integer.class, key);
        assertThat(keys).isZero();

        transactionService.transfer(c1, key, new TransferRequest(from, to, new BigDecimal("60.00"), "retry"));
        assertThat(accounts.findById(from).orElseThrow().getBalance()).isEqualByComparingTo("40.00");
        assertThat(accounts.findById(to).orElseThrow().getBalance()).isEqualByComparingTo("60.00");
        assertThat(reconciliation.findInconsistencies()).isEmpty();
    }

    @Test
    void ledgerIsAppendOnlyAtDatabaseLevel() {
        Long c = newCustomer();
        Long acc = newAccount(c, AccountType.CORRENTE);
        deposit(c, acc, "10.00");

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = 999 WHERE account_id = ?", acc))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE account_id = ?", acc))
                .hasMessageContaining("append-only");
    }

    @Test
    void databaseRejectsUnbalancedLedgerAtCommit() {
        Long c = newCustomer();
        Long acc = newAccount(c, AccountType.CORRENTE);

        assertThatThrownBy(() -> {
            jdbc.execute((java.sql.Connection con) -> {
                con.setAutoCommit(false);
                try (var st = con.createStatement()) {
                    st.execute("INSERT INTO transactions (id, type, amount, created_at) "
                            + "VALUES (900000001, 'DEPOSIT', 10.00, now())");
                    st.execute("INSERT INTO ledger_entries (transaction_id, account_id, direction, amount, balance_after, created_at) "
                            + "VALUES (900000001, " + acc + ", 'CREDIT', 10.00, 10.00, now())");
                    con.commit();
                } catch (java.sql.SQLException e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
                return null;
            });
        }).hasMessageContaining("desbalanceado");
    }
}
