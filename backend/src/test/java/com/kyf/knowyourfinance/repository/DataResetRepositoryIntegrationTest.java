package com.kyf.knowyourfinance.repository;

import com.kyf.knowyourfinance.model.MonthlyBudget;
import com.kyf.knowyourfinance.model.Transaction;
import com.kyf.knowyourfinance.model.TransactionCategory;
import com.kyf.knowyourfinance.model.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the bulk delete queries against a real PostgreSQL: they must
 * remove one user's data and leave everyone else's alone.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class DataResetRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private MonthlyBudgetRepository budgets;

    private Transaction tx(String description, long userId) {
        return new Transaction(LocalDate.parse("2026-09-02"), description, new BigDecimal("5.75"),
                TransactionType.EXPENSE, TransactionCategory.DINING, userId);
    }

    @Test
    void deletingOneUsersDataLeavesTheOtherUsersDataAlone() {
        transactions.save(tx("COFFEE", 1L));
        transactions.save(tx("LUNCH", 1L));
        transactions.save(tx("OTHER PERSON", 2L));
        budgets.save(new MonthlyBudget(1L, LocalDate.parse("2026-09-01"),
                TransactionCategory.DINING, new BigDecimal("100.00")));
        budgets.save(new MonthlyBudget(2L, LocalDate.parse("2026-09-01"),
                TransactionCategory.DINING, new BigDecimal("50.00")));

        assertEquals(2, transactions.countByUserId(1L));
        assertEquals(2, transactions.deleteAllForUser(1L));
        assertEquals(1, budgets.deleteAllForUser(1L));

        assertEquals(0, transactions.countByUserId(1L));
        assertEquals(0, budgets.countByUserId(1L));
        assertEquals(1, transactions.countByUserId(2L));
        assertEquals(1, budgets.countByUserId(2L));
    }
}
