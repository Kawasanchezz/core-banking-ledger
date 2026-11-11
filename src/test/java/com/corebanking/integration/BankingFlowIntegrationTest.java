package com.corebanking.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fluxo completo pela API HTTP (segurança, validação, idempotência, extrato). */
class BankingFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    record Client(String token, long customerId) {
    }

    private Client registerAndLogin() throws Exception {
        String email = uniqueEmail();
        String password = "senha-forte-123";
        MvcResult reg = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Maria Silva", "email", email, "password", password, "cpf", randomCpf()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").value(org.hamcrest.Matchers.startsWith("***")))
                .andReturn();
        long customerId = body(reg).get("id").asLong();

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return new Client(body(login).get("accessToken").asText(), customerId);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, Client c) {
        return b.header("Authorization", "Bearer " + c.token()).contentType(MediaType.APPLICATION_JSON);
    }

    private long openAccount(Client c, String type) throws Exception {
        MvcResult r = mvc.perform(auth(post("/api/v1/accounts"), c).content("{\"type\":\"" + type + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andReturn();
        return body(r).get("id").asLong();
    }

    private MvcResult deposit(Client c, long account, String amount, String key) throws Exception {
        return mvc.perform(auth(post("/api/v1/accounts/" + account + "/deposits"), c)
                        .header("Idempotency-Key", key)
                        .content("{\"amount\":" + amount + ",\"description\":\"dep\"}"))
                .andReturn();
    }

    private MvcResult transfer(Client c, long from, long to, String amount, String key) throws Exception {
        return mvc.perform(auth(post("/api/v1/transfers"), c).header("Idempotency-Key", key)
                        .content("{\"sourceAccountId\":" + from + ",\"targetAccountId\":" + to
                                + ",\"amount\":" + amount + ",\"description\":\"pix\"}"))
                .andReturn();
    }

    private String balance(Client c, long account) throws Exception {
        MvcResult r = mvc.perform(auth(get("/api/v1/accounts/" + account + "/balance"), c))
                .andExpect(status().isOk()).andReturn();
        return new java.math.BigDecimal(body(r).get("balance").asText()).setScale(2).toPlainString();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void fullFlowDepositTransferStatementAndReconciliation() throws Exception {
        Client alice = registerAndLogin();
        Client bob = registerAndLogin();
        long a = openAccount(alice, "CORRENTE");
        long b = openAccount(bob, "POUPANCA");

        assertThat(deposit(alice, a, "500.00", key()).getResponse().getStatus()).isEqualTo(201);
        MvcResult tr = transfer(alice, a, b, "200.50", key());
        assertThat(tr.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(tr).get("type").asText()).isEqualTo("TRANSFER");

        assertThat(balance(alice, a)).isEqualTo("299.50");
        assertThat(balance(bob, b)).isEqualTo("200.50");

        // extrato da Alice: mais recente primeiro (transferência/débito, depois depósito/crédito)
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/statement"), alice)
                        .param("from", today.minusDays(1).toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].amount").value(200.50))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(299.50))
                .andExpect(jsonPath("$.content[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.content[1].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[1].balanceAfter").value(500.00));

        // paginação
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/statement"), alice).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.totalPages").value(2));

        // filtro por período: futuro e passado não retornam nada
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/statement"), alice)
                        .param("from", today.plusDays(1).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/statement"), alice)
                        .param("to", today.minusDays(1).toString()))
                .andExpect(jsonPath("$.totalElements").value(0));

        // período invertido
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/statement"), alice)
                        .param("from", today.plusDays(1).toString()).param("to", today.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

        // reconciliação: saldo == ledger
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/reconciliation"), alice))
                .andExpect(jsonPath("$.consistent").value(true))
                .andExpect(jsonPath("$.ledgerBalance").value(299.50));
    }

    @Test
    void withdrawalReducesBalanceAndInsufficientFundsLeavesNothingBehind() throws Exception {
        Client c = registerAndLogin();
        long acc = openAccount(c, "CORRENTE");
        deposit(c, acc, "100.00", key());

        mvc.perform(auth(post("/api/v1/accounts/" + acc + "/withdrawals"), c).header("Idempotency-Key", key())
                        .content("{\"amount\":40.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(60.00));

        mvc.perform(auth(post("/api/v1/accounts/" + acc + "/withdrawals"), c).header("Idempotency-Key", key())
                        .content("{\"amount\":60.01}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.correlationId").exists());

        assertThat(balance(c, acc)).isEqualTo("60.00");
        mvc.perform(auth(get("/api/v1/accounts/" + acc + "/statement"), c))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void retryWithSameIdempotencyKeyDoesNotDuplicateTransfer() throws Exception {
        Client alice = registerAndLogin();
        Client bob = registerAndLogin();
        long a = openAccount(alice, "CORRENTE");
        long b = openAccount(bob, "CORRENTE");
        deposit(alice, a, "1000.00", key());

        String idem = key();
        MvcResult first = transfer(alice, a, b, "100.00", idem);
        MvcResult retry = transfer(alice, a, b, "100.00", idem);

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(first.getResponse().getHeader("Idempotent-Replayed")).isNull();
        assertThat(body(retry).get("transactionId")).isEqualTo(body(first).get("transactionId"));

        assertThat(balance(alice, a)).isEqualTo("900.00");
        assertThat(balance(bob, b)).isEqualTo("100.00");
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() throws Exception {
        Client alice = registerAndLogin();
        Client bob = registerAndLogin();
        long a = openAccount(alice, "CORRENTE");
        long b = openAccount(bob, "CORRENTE");
        deposit(alice, a, "1000.00", key());

        String idem = key();
        transfer(alice, a, b, "100.00", idem);
        MvcResult different = transfer(alice, a, b, "250.00", idem);

        assertThat(different.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(different).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(balance(alice, a)).isEqualTo("900.00");
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        Client c = registerAndLogin();
        long acc = openAccount(c, "CORRENTE");
        mvc.perform(auth(post("/api/v1/accounts/" + acc + "/deposits"), c).content("{\"amount\":10.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_MISSING"));
    }

    @Test
    void requiresAuthenticationAndHidesOtherCustomersAccounts() throws Exception {
        Client alice = registerAndLogin();
        Client mallory = registerAndLogin();
        long a = openAccount(alice, "CORRENTE");
        deposit(alice, a, "100.00", key());

        mvc.perform(get("/api/v1/accounts/" + a + "/balance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts/" + a + "/balance").header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(auth(get("/api/v1/accounts/" + a), mallory)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/balance"), mallory)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/v1/accounts/" + a + "/statement"), mallory)).andExpect(status().isForbidden());
        assertThat(deposit(mallory, a, "5.00", key()).getResponse().getStatus()).isEqualTo(403);

        long m = openAccount(mallory, "CORRENTE");
        assertThat(transfer(mallory, a, m, "10.00", key()).getResponse().getStatus()).isEqualTo(403);
        assertThat(balance(alice, a)).isEqualTo("100.00");
        mvc.perform(auth(get("/api/v1/accounts/999999"), mallory)).andExpect(status().isNotFound());
    }

    @Test
    void validatesInput() throws Exception {
        // CPF inválido, e-mail inválido, senha curta
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"nao-e-email\",\"password\":\"123\",\"cpf\":\"111.111.111-11\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.cpf").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        Client c = registerAndLogin();
        long acc = openAccount(c, "CORRENTE");
        long acc2 = openAccount(c, "POUPANCA");

        for (String badAmount : new String[]{"0", "-5", "10.001"}) {
            assertThat(deposit(c, acc, badAmount, key()).getResponse().getStatus()).isEqualTo(400);
        }
        // mesma conta
        MvcResult same = transfer(c, acc, acc, "1.00", key());
        assertThat(same.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(same).get("code").asText()).isEqualTo("SAME_ACCOUNT_TRANSFER");
        // tipo CAIXA não pode ser aberto por cliente
        mvc.perform(auth(post("/api/v1/accounts"), c).content("{\"type\":\"CAIXA\"}"))
                .andExpect(status().isBadRequest());
        // transferir para a conta CAIXA do banco é proibido
        deposit(c, acc, "10.00", key());
        assertThat(transfer(c, acc, 1L, "1.00", key()).getResponse().getStatus()).isEqualTo(404);
        // login errado
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@x.com\",\"password\":\"qualquer-senha\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        // listagem
        mvc.perform(auth(get("/api/v1/accounts"), c)).andExpect(jsonPath("$.length()").value(2));
        assertThat(acc2).isNotEqualTo(acc);
    }

    @Test
    void duplicateCpfAndEmailAreRejected() throws Exception {
        String cpf = randomCpf();
        String email = uniqueEmail();
        String payload = json.writeValueAsString(Map.of("name", "A", "email", email, "password", "senha-forte-123", "cpf", cpf));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "B", "email", uniqueEmail(),
                                "password", "senha-forte-123", "cpf", cpf))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CPF"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "C", "email", email.toUpperCase(),
                                "password", "senha-forte-123", "cpf", randomCpf()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void healthEndpointsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
