package com.corebanking.api.controller;

import com.corebanking.api.dto.TransactionDtos.TransactionResponse;
import com.corebanking.api.dto.TransactionDtos.TransferRequest;
import com.corebanking.application.TransactionService;
import com.corebanking.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transferências")
public class TransferController {

    private final TransactionService transactions;

    public TransferController(TransactionService transactions) {
        this.transactions = transactions;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(required = true, description = "Chave única da operação (retry seguro)")
            @RequestHeader(AccountController.IDEMPOTENCY_HEADER) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return AccountController.created(transactions.transfer(user.customerId(), idempotencyKey, request));
    }
}
