package com.kyf.knowyourfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyf.knowyourfinance.model.Transaction;
import com.kyf.knowyourfinance.model.TransactionCategory;
import com.kyf.knowyourfinance.model.TransactionType;
import com.kyf.knowyourfinance.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AccountIsolationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TransactionRepository transactions;

    private static final String TRANSACTION = """
        {"date":"2026-09-02","description":"Synthetic coffee","amount":4.25,"type":"EXPENSE","userId":42}
        """;

    @Test
    void sessionAndFinancialDataAreIsolated() throws Exception {
        Transaction legacy = transactions.save(new Transaction(LocalDate.parse("2026-09-02"), "Legacy test",
                new BigDecimal("5.75"), TransactionType.EXPENSE, TransactionCategory.DINING, 42L));
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());

        MockHttpSession first = credentials("signup", " First@Example.test ");
        MockHttpSession second = credentials("signup", "second@example.test");

        mvc.perform(get("/api/auth/session").session(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("first@example.test"));
        mvc.perform(get("/api/transactions").session(first).param("userId", "42"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/transactions/{id}", legacy.getId()).session(first))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/transactions").session(first)
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSACTION))
                .andExpect(status().isForbidden());

        String created = mvc.perform(withCsrf(post("/api/transactions"), first)
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSACTION))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        long owner = json.readTree(created).get("userId").asLong();
        assertNotEquals(42L, owner);

        mvc.perform(get("/api/transactions/{id}", id).session(second).param("userId", Long.toString(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(withCsrf(put("/api/transactions/{id}", id), second)
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSACTION))
                .andExpect(status().isNotFound());
        mvc.perform(withCsrf(delete("/api/transactions/{id}", id), second))
                .andExpect(status().isNotFound());
        // The filtered CSV feature exports rows returned by the transaction list.
        mvc.perform(get("/api/transactions").session(second).param("userId", Long.toString(owner)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(withCsrf(delete("/api/data"), second).param("userId", Long.toString(owner))
                        .param("confirm", "DELETE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deletedTransactions").value(0));
        assertTrue(transactions.existsById(id));
        assertTrue(transactions.existsById(legacy.getId()));

        mvc.perform(withCsrf(post("/api/auth/signout"), first)).andExpect(status().isNoContent());
        assertTrue(first.isInvalid());
        MockHttpSession restored = credentials("signin", "FIRST@example.test");
        mvc.perform(get("/api/transactions").session(restored))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
    }

    private MockHttpSession credentials(String action, String email) throws Exception {
        var session = new MockHttpSession();
        String preAuthId = session.getId();
        var request = post("/api/auth/{action}", action).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "Synthetic test password")));
        var authenticated = (MockHttpSession) mvc.perform(withCsrf(request, session))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertNotEquals(preAuthId, authenticated.getId());
        return authenticated;
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request,
                                                   MockHttpSession session) throws Exception {
        String body = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var csrf = json.readTree(body);
        return request.session(session).header(csrf.get("headerName").asText(), csrf.get("token").asText());
    }
}
