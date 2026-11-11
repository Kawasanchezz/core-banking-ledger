package com.corebanking.application;

import com.corebanking.api.dto.AccountDtos.ReconciliationResponse;
import com.corebanking.domain.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Reconciliação: o saldo materializado da conta deve ser igual a soma(créditos) - soma(débitos) do ledger. */
@Service
public class ReconciliationService {

    private static final String LEDGER_BALANCE = """
            COALESCE(SUM(CASE l.direction WHEN 'CREDIT' THEN l.amount ELSE -l.amount END), 0)
            """;

    private final JdbcTemplate jdbc;
    private final AccountService accountService;

    public ReconciliationService(JdbcTemplate jdbc, AccountService accountService) {
        this.jdbc = jdbc;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse check(Long customerId, Long accountId) {
        Account a = accountService.requireOwned(customerId, accountId);
        BigDecimal ledger = jdbc.queryForObject(
                "SELECT " + LEDGER_BALANCE + " FROM ledger_entries l WHERE l.account_id = ?",
                BigDecimal.class, accountId);
        return new ReconciliationResponse(accountId, a.getBalance(), ledger, a.getBalance().compareTo(ledger) == 0);
    }

    /** Todas as contas cujo saldo materializado diverge do ledger (esperado: lista vazia). */
    @Transactional(readOnly = true)
    public List<ReconciliationResponse> findInconsistencies() {
        return jdbc.query("""
                SELECT a.id, a.balance, %s AS ledger_balance
                  FROM accounts a LEFT JOIN ledger_entries l ON l.account_id = a.id
                 GROUP BY a.id, a.balance
                HAVING a.balance <> %s
                """.formatted(LEDGER_BALANCE, LEDGER_BALANCE),
                (rs, i) -> new ReconciliationResponse(rs.getLong("id"), rs.getBigDecimal("balance"),
                        rs.getBigDecimal("ledger_balance"), false));
    }
}
