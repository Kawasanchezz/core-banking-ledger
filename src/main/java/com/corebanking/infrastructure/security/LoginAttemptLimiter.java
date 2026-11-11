package com.corebanking.infrastructure.security;

import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freio a força bruta no login: conta falhas em janela deslizante por e-mail e por IP.
 * Em memória, portanto por instância: com várias réplicas o limite efetivo é multiplicado
 * (um rate limit no proxy/gateway complementa). Atrás de um proxy, o IP visto é o do proxy
 * a menos que o encaminhamento de cabeçalhos esteja configurado e confiável.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES_PER_EMAIL = 10;
    static final int MAX_FAILURES_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAllowed(String email, String ip) {
        if (count(emailKey(email)) >= MAX_FAILURES_PER_EMAIL || count(ipKey(ip)) >= MAX_FAILURES_PER_IP) {
            throw new BusinessException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "Muitas tentativas de login. Tente novamente mais tarde.");
        }
    }

    public void recordFailure(String email, String ip) {
        add(emailKey(email));
        add(ipKey(ip));
        if (failures.size() > PRUNE_THRESHOLD) {
            failures.keySet().forEach(this::count);
        }
    }

    public void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    private void add(String key) {
        Instant now = clock.instant();
        failures.compute(key, (k, window) -> {
            Deque<Instant> d = window == null ? new ArrayDeque<>() : window;
            evictExpired(d, now);
            d.addLast(now);
            return d;
        });
    }

    private int count(String key) {
        Instant now = clock.instant();
        int[] size = {0};
        failures.computeIfPresent(key, (k, d) -> {
            evictExpired(d, now);
            size[0] = d.size();
            return d.isEmpty() ? null : d;
        });
        return size[0];
    }

    private static void evictExpired(Deque<Instant> d, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!d.isEmpty() && d.peekFirst().isBefore(cutoff)) {
            d.pollFirst();
        }
    }

    private static String emailKey(String email) {
        return "e:" + email;
    }

    private static String ipKey(String ip) {
        return "i:" + ip;
    }
}
