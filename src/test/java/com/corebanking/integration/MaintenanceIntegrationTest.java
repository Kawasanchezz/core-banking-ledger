package com.corebanking.integration;

import com.corebanking.application.IdempotencyService;
import com.corebanking.application.ReconciliationService;
import com.corebanking.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired IdempotencyService idempotency;
    @Autowired ReconciliationService reconciliation;
    @Autowired JdbcTemplate jdbc;

    @Test
    void expiredIdempotencyKeysArePurged() {
        Long c = newCustomer();
        Long acc = newAccount(c, AccountType.CORRENTE);
        deposit(c, acc, "10.00");
        jdbc.update("UPDATE idempotency_keys SET created_at = now() - interval '2 days' WHERE owner_id = ?", c);

        assertThat(idempotency.purgeExpired()).isGreaterThanOrEqualTo(1);
        Integer left = jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE owner_id = ?", Integer.class, c);
        assertThat(left).isZero();
    }

    @Test
    void reconciliationDetectsTamperedBalance() {
        Long c = newCustomer();
        Long acc = newAccount(c, AccountType.CORRENTE);
        deposit(c, acc, "10.00");
        assertThat(reconciliation.check(c, acc).consistent()).isTrue();

        jdbc.update("UPDATE accounts SET balance = 999.00 WHERE id = ?", acc);
        assertThat(reconciliation.check(c, acc).consistent()).isFalse();
        assertThat(reconciliation.findInconsistencies()).extracting(r -> r.accountId()).contains(acc);

        jdbc.update("UPDATE accounts SET balance = 10.00 WHERE id = ?", acc);
        assertThat(reconciliation.findInconsistencies()).isEmpty();
    }
}
