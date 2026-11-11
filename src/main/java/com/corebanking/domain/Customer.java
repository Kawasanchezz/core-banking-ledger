package com.corebanking.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Somente dígitos. */
    @Column(nullable = false, length = 11, updatable = false)
    private String cpf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Customer() {
    }

    public Customer(String name, String cpf, Instant createdAt) {
        this.name = name;
        this.cpf = cpf;
        this.createdAt = createdAt;
    }
}
