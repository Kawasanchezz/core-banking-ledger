package com.corebanking.domain;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/** A operação de negócio (depósito, saque, transferência). Imutável; os lançamentos ficam em {@link LedgerEntry}. */
@Getter
@Immutable
@Entity
@Table(name = "transactions")
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String description;

    @Column(name = "initiated_by")
    private Long initiatedBy;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BankTransaction() {
    }

    public BankTransaction(TransactionType type, BigDecimal amount, String description, Long initiatedBy,
                           String idempotencyKey, Instant createdAt) {
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }
}
