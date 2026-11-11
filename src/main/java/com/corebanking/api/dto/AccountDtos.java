package com.corebanking.api.dto;

import com.corebanking.domain.Account;
import com.corebanking.domain.AccountStatus;
import com.corebanking.domain.AccountType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record OpenAccountRequest(@NotNull AccountType type) {
    }

    public record AccountResponse(Long id, String agency, String number, AccountType type, AccountStatus status,
                                  BigDecimal balance, BigDecimal overdraftLimit, Instant createdAt) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getAgency(), a.getNumber(), a.getType(), a.getStatus(),
                    a.getBalance(), a.getOverdraftLimit(), a.getCreatedAt());
        }
    }

    public record BalanceResponse(Long accountId, BigDecimal balance, BigDecimal availableBalance, Instant asOf) {
    }

    public record ReconciliationResponse(Long accountId, BigDecimal storedBalance, BigDecimal ledgerBalance,
                                         boolean consistent) {
    }
}
