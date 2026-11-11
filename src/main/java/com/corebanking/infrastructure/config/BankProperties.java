package com.corebanking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "bank")
public record BankProperties(Jwt jwt, AccountSettings account, Idempotency idempotency) {

    /** Segredo vem de variável de ambiente (JWT_SECRET); mínimo 32 bytes (HS256). */
    public record Jwt(String secret, long expirationMinutes) {
    }

    public record AccountSettings(String agency, BigDecimal checkingOverdraftLimit) {
    }

    public record Idempotency(Duration ttl) {
    }
}
