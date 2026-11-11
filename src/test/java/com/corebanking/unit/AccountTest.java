package com.corebanking.unit;

import com.corebanking.domain.Account;
import com.corebanking.domain.AccountStatus;
import com.corebanking.domain.AccountType;
import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static Account account(AccountType type, String overdraft) {
        return Account.open("0001", "123", type, 1L, new BigDecimal(overdraft), Instant.now());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static void assertCode(Runnable r, ErrorCode code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void startsActiveWithZeroBalance() {
        Account a = account(AccountType.CORRENTE, "0");
        assertThat(a.getStatus()).isEqualTo(AccountStatus.ATIVA);
        assertThat(a.getBalance()).isEqualByComparingTo("0");
        assertThat(a.isOwnedBy(1L)).isTrue();
        assertThat(a.isOwnedBy(2L)).isFalse();
    }

    @Test
    void creditAndDebitUpdateBalance() {
        Account a = account(AccountType.CORRENTE, "0");
        a.credit(bd("100.00"));
        a.debit(bd("40.25"));
        assertThat(a.getBalance()).isEqualByComparingTo("59.75");
    }

    @Test
    void savingsNeverGoesNegativeEvenIfOverdraftIsRequested() {
        Account a = account(AccountType.POUPANCA, "1000");
        assertThat(a.getOverdraftLimit()).isEqualByComparingTo("0");
        a.credit(bd("10.00"));
        assertCode(() -> a.debit(bd("10.01")), ErrorCode.INSUFFICIENT_FUNDS);
        a.debit(bd("10.00"));
        assertThat(a.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void checkingRespectsOverdraftLimit() {
        Account a = account(AccountType.CORRENTE, "200");
        a.debit(bd("200.00"));
        assertThat(a.getBalance()).isEqualByComparingTo("-200.00");
        assertCode(() -> a.debit(bd("0.01")), ErrorCode.INSUFFICIENT_FUNDS);
    }

    @Test
    void failedDebitDoesNotChangeBalance() {
        Account a = account(AccountType.CORRENTE, "0");
        a.credit(bd("50.00"));
        assertCode(() -> a.debit(bd("50.01")), ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(a.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void blockedAccountRejectsOperations() {
        Account a = account(AccountType.CORRENTE, "0");
        a.credit(bd("10.00"));
        a.block();
        assertCode(() -> a.debit(bd("1.00")), ErrorCode.ACCOUNT_NOT_ACTIVE);
        assertCode(() -> a.credit(bd("1.00")), ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void customersCannotOpenCashAccount() {
        assertCode(() -> account(AccountType.CAIXA, "0"), ErrorCode.INVALID_ACCOUNT_TYPE);
    }
}
