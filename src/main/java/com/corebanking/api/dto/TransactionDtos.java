package com.corebanking.api.dto;

import com.corebanking.domain.EntryDirection;
import com.corebanking.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** Depósito e saque. */
    public record AmountRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @Size(max = 140) String description) {
    }

    public record TransferRequest(
            @NotNull Long sourceAccountId,
            @NotNull Long targetAccountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @Size(max = 140) String description) {
    }

    /** {@code balanceAfter} é o saldo da conta do solicitante após a operação. */
    public record TransactionResponse(Long transactionId, TransactionType type, BigDecimal amount,
                                      String description, Long sourceAccountId, Long targetAccountId,
                                      BigDecimal balanceAfter, Instant createdAt) {
    }

    public record StatementEntryResponse(Long entryId, Long transactionId, TransactionType type,
                                         EntryDirection direction, BigDecimal amount, BigDecimal balanceAfter,
                                         String description, Instant createdAt) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
