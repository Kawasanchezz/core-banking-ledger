package com.corebanking.domain;

import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "accounts")
public class Account {

    public static final long CASH_ACCOUNT_ID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, updatable = false)
    private String agency;

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = Money.ZERO;

    @Column(name = "overdraft_limit", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal overdraftLimit = Money.ZERO;

    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public static Account open(String agency, String number, AccountType type, Long customerId,
                               BigDecimal overdraftLimit, Instant now) {
        if (type == AccountType.CAIXA) {
            throw new BusinessException(ErrorCode.INVALID_ACCOUNT_TYPE, "Tipo de conta inválido");
        }
        Account a = new Account();
        a.agency = agency;
        a.number = number;
        a.type = type;
        a.customerId = customerId;
        a.status = AccountStatus.ATIVA;
        a.balance = Money.ZERO;
        a.overdraftLimit = type == AccountType.CORRENTE ? Money.nonNegative(overdraftLimit) : Money.ZERO;
        a.createdAt = now;
        return a;
    }

    public boolean isOwnedBy(Long customerId) {
        return this.customerId != null && this.customerId.equals(customerId);
    }

    public BigDecimal availableBalance() {
        return balance.add(overdraftLimit);
    }

    public void debit(BigDecimal amount) {
        ensureActive();
        if (type != AccountType.CAIXA && amount.compareTo(availableBalance()) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS, "Saldo insuficiente");
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        ensureActive();
        balance = balance.add(amount);
    }

    public void block() {
        status = AccountStatus.BLOQUEADA;
    }

    private void ensureActive() {
        if (status != AccountStatus.ATIVA) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE, "A conta " + number + " não está ativa");
        }
    }
}
