package com.corebanking.api.controller;

import com.corebanking.api.dto.AccountDtos.*;
import com.corebanking.api.dto.TransactionDtos.*;
import com.corebanking.application.*;
import com.corebanking.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Contas")
public class AccountController {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    static final String REPLAY_HEADER = "Idempotent-Replayed";

    private final AccountService accounts;
    private final TransactionService transactions;
    private final StatementService statements;
    private final ReconciliationService reconciliation;

    public AccountController(AccountService accounts, TransactionService transactions,
                             StatementService statements, ReconciliationService reconciliation) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.statements = statements;
        this.reconciliation = reconciliation;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody OpenAccountRequest request) {
        return accounts.open(user.customerId(), request.type());
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accounts.list(user.customerId());
    }

    @GetMapping("/{id}")
    public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return accounts.get(user.customerId(), id);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return accounts.balance(user.customerId(), id);
    }

    @GetMapping("/{id}/statement")
    public PageResponse<StatementEntryResponse> statement(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return statements.statement(user.customerId(), id, from, to, page, size);
    }

    @GetMapping("/{id}/reconciliation")
    public ReconciliationResponse reconciliation(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable Long id) {
        return reconciliation.check(user.customerId(), id);
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Parameter(required = true, description = "Chave única da operação (retry seguro)")
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return created(transactions.deposit(user.customerId(), id, idempotencyKey, request));
    }

    @PostMapping("/{id}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Parameter(required = true, description = "Chave única da operação (retry seguro)")
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {
        return created(transactions.withdraw(user.customerId(), id, idempotencyKey, request));
    }

    static ResponseEntity<TransactionResponse> created(IdempotentResult<TransactionResponse> result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.CREATED);
        if (result.replayed()) {
            builder.header(REPLAY_HEADER, "true");
        }
        return builder.body(result.body());
    }
}
