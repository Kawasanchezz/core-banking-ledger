package com.corebanking.unit;

import com.corebanking.infrastructure.config.BankProperties;
import com.corebanking.infrastructure.security.AuthenticatedUser;
import com.corebanking.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private static BankProperties props(String secret) {
        return new BankProperties(new BankProperties.Jwt(secret, 10),
                new BankProperties.AccountSettings("0001", BigDecimal.ZERO),
                new BankProperties.Idempotency(Duration.ofHours(24)));
    }

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void roundTrip() {
        JwtService jwt = new JwtService(props(SECRET), NOW);
        String token = jwt.generate(new AuthenticatedUser(7L, 9L, "a@b.com"));

        assertThat(jwt.parse(token)).contains(new AuthenticatedUser(7L, 9L, "a@b.com"));
        assertThat(jwt.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void rejectsExpiredTamperedAndGarbageTokens() {
        JwtService jwt = new JwtService(props(SECRET), NOW);
        String token = jwt.generate(new AuthenticatedUser(7L, 9L, "a@b.com"));

        Clock later = Clock.fixed(NOW.instant().plus(Duration.ofMinutes(11)), ZoneOffset.UTC);
        assertThat(new JwtService(props(SECRET), later).parse(token)).isEmpty();

        JwtService otherKey = new JwtService(props("another-secret-another-secret-0123456789"), NOW);
        assertThat(otherKey.parse(token)).isEmpty();

        assertThat(jwt.parse(token + "x")).isEmpty();
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void rejectsUnsignedTokenWithAlgNone() {
        JwtService jwt = new JwtService(props(SECRET), NOW);
        var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long exp = NOW.instant().plus(Duration.ofMinutes(5)).getEpochSecond();
        String payload = encoder.encodeToString(
                ("{\"sub\":\"7\",\"cid\":9,\"email\":\"a@b.com\",\"exp\":" + exp + "}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(jwt.parse(header + "." + payload + ".")).isEmpty();
    }

    @Test
    void shortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtService(props("short"), NOW)).isInstanceOf(IllegalStateException.class);
    }
}
