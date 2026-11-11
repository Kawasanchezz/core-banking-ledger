package com.corebanking.unit;

import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.security.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    @Test
    void blocksAnEmailAfterTenFailuresEvenFromAnotherIp() {
        for (int i = 0; i < 10; i++) {
            limiter.checkAllowed("a@b.com", "10.0.0." + i);
            limiter.recordFailure("a@b.com", "10.0.0." + i);
        }

        assertThatThrownBy(() -> limiter.checkAllowed("a@b.com", "10.9.9.9"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS));
        assertThatCode(() -> limiter.checkAllowed("outro@b.com", "10.9.9.9")).doesNotThrowAnyException();
    }

    @Test
    void blocksAnIpAfterThirtyFailuresAcrossEmails() {
        for (int i = 0; i < 30; i++) {
            limiter.recordFailure("user" + i + "@b.com", "10.0.0.1");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("novo@b.com", "10.0.0.1"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> limiter.checkAllowed("novo@b.com", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void failuresExpireAfterTheWindow() {
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("a@b.com", "10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("a@b.com", "10.0.0.1")).isInstanceOf(BusinessException.class);

        clock.now = clock.now.plus(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed("a@b.com", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsTheEmailCounter() {
        for (int i = 0; i < 9; i++) {
            limiter.recordFailure("a@b.com", "10.0.0." + i);
        }
        limiter.recordSuccess("a@b.com");
        limiter.recordFailure("a@b.com", "10.0.1.1");

        assertThatCode(() -> limiter.checkAllowed("a@b.com", "10.0.2.2")).doesNotThrowAnyException();
    }
}
