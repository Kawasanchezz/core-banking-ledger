package com.corebanking.integration;

import com.corebanking.domain.AccountType;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regressão dos achados da auditoria de segurança. */
class SecurityHardeningIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String tokenFor(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asString();
    }

    @Test
    void loginIsBlockedAfterRepeatedFailuresEvenWithTheCorrectPassword() throws Exception {
        String email = uniqueEmail();
        authService.register(new com.corebanking.api.dto.AuthDtos.RegisterRequest(
                "Maria", email, "senha-forte-123", randomCpf()));
        String wrong = json.writeValueAsString(Map.of("email", email, "password", "senha-errada-999"));
        String right = json.writeValueAsString(Map.of("email", email, "password", "senha-forte-123"));

        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/auth/login").with(from("10.1.1." + i)).contentType(MediaType.APPLICATION_JSON)
                            .content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").with(from("10.1.1.200")).contentType(MediaType.APPLICATION_JSON)
                        .content(right))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void oversizedBodyIsRejectedBeforeBeingRead() throws Exception {
        String huge = "{\"name\":\"" + "A".repeat(20_000) + "\"}";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void statementRejectsAbsurdDatesInsteadOfFailingWithServerError() throws Exception {
        String token = tokenFor(registerAndGetEmail(), "senha-forte-123");
        Long own = ownAccount(token);

        for (String param : new String[]{"to", "from"}) {
            mvc.perform(get("/api/v1/accounts/" + own + "/statement").header("Authorization", "Bearer " + token)
                            .param(param, "+999999999-12-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        }
        mvc.perform(get("/api/v1/accounts/" + own + "/statement").header("Authorization", "Bearer " + token)
                        .param("from", "1969-12-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void securityHeadersAreSentOnApiAndStaticPages() throws Exception {
        for (String path : new String[]{"/api/v1/accounts", "/", "/app.js"}) {
            mvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy",
                            org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().exists("Permissions-Policy"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }
    }

    @Test
    void staticFrontendIsPublicButApiIsNot() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void reconciliationOfAnotherCustomersAccountIsForbidden() throws Exception {
        Long owner = newCustomer();
        Long account = newAccount(owner, AccountType.CORRENTE);
        String email = registerAndGetEmail();
        String token = tokenFor(email, "senha-forte-123");

        mvc.perform(get("/api/v1/accounts/" + account + "/reconciliation").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String registerAndGetEmail() {
        String email = uniqueEmail();
        authService.register(new com.corebanking.api.dto.AuthDtos.RegisterRequest(
                "Cliente", email, "senha-forte-123", randomCpf()));
        return email;
    }

    private Long ownAccount(String token) throws Exception {
        String body = mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CORRENTE\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
