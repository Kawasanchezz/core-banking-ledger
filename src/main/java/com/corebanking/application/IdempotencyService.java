package com.corebanking.application;

import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import com.corebanking.infrastructure.config.BankProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int MAX_KEY_LENGTH = 255;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BankProperties props;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper, BankProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    public static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_MISSING,
                    "O header Idempotency-Key é obrigatório (até " + MAX_KEY_LENGTH + " caracteres)");
        }
    }

    public static String fingerprint(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            sb.append(p == null ? "" : p).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> IdempotentResult<T> execute(Long ownerId, String key, String requestHash, Class<T> type,
                                           Supplier<T> action) {
        validateKey(key);
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys (owner_id, idem_key, request_hash, status, created_at)
                VALUES (?, ?, ?, 'IN_PROGRESS', ?)
                ON CONFLICT (owner_id, idem_key) DO NOTHING
                """, ownerId, key, requestHash, Timestamp.from(clock.instant()));

        if (inserted == 1) {
            T result = action.get();
            jdbc.update("""
                    UPDATE idempotency_keys
                       SET status = 'COMPLETED', response_status = 201, response_body = ?
                     WHERE owner_id = ? AND idem_key = ?
                    """, serialize(result), ownerId, key);
            return new IdempotentResult<>(result, false);
        }

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT request_hash, response_body FROM idempotency_keys WHERE owner_id = ? AND idem_key = ?",
                ownerId, key);
        if (!requestHash.equals(row.get("request_hash"))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Esta Idempotency-Key já foi usada com um payload diferente");
        }
        log.info("Replay idempotente: resposta original devolvida sem reprocessar");
        return new IdempotentResult<>(deserialize((String) row.get("response_body"), type), true);
    }

    @Transactional
    public int purgeExpired() {
        Instant limit = clock.instant().minus(props.idempotency().ttl());
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at < ?", Timestamp.from(limit));
    }

    private String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Falha ao serializar resposta idempotente", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Falha ao ler resposta idempotente", e);
        }
    }
}
