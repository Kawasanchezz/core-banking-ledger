package com.corebanking.integration;

import com.corebanking.api.dto.AuthDtos.RegisterRequest;
import com.corebanking.api.dto.TransactionDtos.AmountRequest;
import com.corebanking.application.AccountService;
import com.corebanking.application.AuthService;
import com.corebanking.application.TransactionService;
import com.corebanking.domain.AccountType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/** PostgreSQL real via Testcontainers (um container compartilhado por toda a suíte). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Por padrão sobe um PostgreSQL via Testcontainers. Se TEST_DB_URL estiver definido (ex.: CI/execução
     * dentro de um container sem acesso ao socket do Docker), usa esse PostgreSQL externo e VAZIO.
     */
    private static final String EXTERNAL_URL = System.getenv("TEST_DB_URL");
    static final PostgreSQLContainer POSTGRES =
            EXTERNAL_URL == null ? new PostgreSQLContainer("postgres:16-alpine") : null;

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "corebanking"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "corebanking"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired protected AuthService authService;
    @Autowired protected AccountService accountService;
    @Autowired protected TransactionService transactionService;

    /** Cria cliente (CPF válido aleatório e e-mail único) e devolve o customerId. */
    protected Long newCustomer() {
        return authService.register(new RegisterRequest("Cliente Teste",
                uniqueEmail(), "senha-forte-123", randomCpf())).id();
    }

    protected Long newAccount(Long customerId, AccountType type) {
        return accountService.open(customerId, type).id();
    }

    protected void deposit(Long customerId, Long accountId, String amount) {
        transactionService.deposit(customerId, accountId, "seed-" + java.util.UUID.randomUUID(),
                new AmountRequest(new BigDecimal(amount), "seed"));
    }

    protected static String uniqueEmail() {
        return "user" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@test.com";
    }

    /** Gera um CPF com dígitos verificadores corretos (implementação independente da de produção). */
    protected static String randomCpf() {
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                d[i] = ThreadLocalRandom.current().nextInt(10);
            }
        } while (allEqual(d));
        d[9] = verifier(d, 9);
        d[10] = verifier(d, 10);
        StringBuilder sb = new StringBuilder();
        for (int x : d) {
            sb.append(x);
        }
        return sb.toString();
    }

    private static boolean allEqual(int[] d) {
        for (int i = 1; i < 9; i++) {
            if (d[i] != d[0]) {
                return false;
            }
        }
        return true;
    }

    private static int verifier(int[] d, int len) {
        int sum = 0;
        for (int i = 0; i < len; i++) {
            sum += d[i] * (len + 1 - i);
        }
        int r = (sum * 10) % 11;
        return r == 10 ? 0 : r;
    }
}
