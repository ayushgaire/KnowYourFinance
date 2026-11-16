package com.kyf.knowyourfinance.service;

import com.kyf.knowyourfinance.model.*;
import com.kyf.knowyourfinance.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {
    @Mock MonthlyBudgetRepository budgets;
    @Mock TransactionRepository transactions;
    final YearMonth month = YearMonth.of(2028, 2);
    BudgetService service() { return new BudgetService(budgets, transactions); }
    Transaction transaction(String amount, TransactionType type, TransactionCategory category) {
        return new Transaction(LocalDate.of(2028, 2, 29), "Example", new BigDecimal(amount), type, category, 7L);
    }
    @Test void sumsOnlyExpensesAndUsesExactMoneyAndLeapMonthBoundaries() {
        when(transactions.findByUserIdAndDateBetween(7L, month.atDay(1), month.atEndOfMonth())).thenReturn(List.of(
                transaction("0.10", TransactionType.EXPENSE, TransactionCategory.DINING),
                transaction("0.20", TransactionType.EXPENSE, TransactionCategory.DINING),
                transaction("900", TransactionType.INCOME, TransactionCategory.DINING),
                transaction("5", TransactionType.EXPENSE, TransactionCategory.OTHER)));
        when(budgets.findByUserIdAndMonthStartOrderByCategory(7L, month.atDay(1))).thenReturn(List.of(
                new MonthlyBudget(7L, month.atDay(1), TransactionCategory.DINING, new BigDecimal("1.00"))));
        var result = service().progress(7L, month).getFirst();
        assertEquals(new BigDecimal("0.30"), result.spent());
        assertEquals(new BigDecimal("0.70"), result.remaining());
        assertEquals("ON_TRACK", result.status());
        verify(transactions).findByUserIdAndDateBetween(7L, LocalDate.of(2028,2,1), LocalDate.of(2028,2,29));
    }
    @Test void alertsAtExactThresholdsWithoutUsingRoundedPercentageForStatus() {
        for (String amount : List.of("79.9999", "80", "100", "100.01")) {
            when(transactions.findByUserIdAndDateBetween(7L, month.atDay(1), month.atEndOfMonth()))
                    .thenReturn(List.of(transaction(amount, TransactionType.EXPENSE, TransactionCategory.DINING)));
            when(budgets.findByUserIdAndMonthStartOrderByCategory(7L, month.atDay(1))).thenReturn(List.of(
                    new MonthlyBudget(7L, month.atDay(1), TransactionCategory.DINING, new BigDecimal("100"))));
            String expected = switch (amount) { case "79.9999" -> "ON_TRACK"; case "80" -> "WARNING"; case "100" -> "REACHED"; default -> "OVER"; };
            assertEquals(expected, service().progress(7L, month).getFirst().status());
        }
    }
    @Test void uncategorizedExpensesCountAsOtherAndEmptyCategoriesStartAtZero() {
        when(transactions.findByUserIdAndDateBetween(7L, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(List.of(transaction("15", TransactionType.EXPENSE, null)));
        when(budgets.findByUserIdAndMonthStartOrderByCategory(7L, month.atDay(1))).thenReturn(List.of(
                new MonthlyBudget(7L, month.atDay(1), TransactionCategory.OTHER, new BigDecimal("10")),
                new MonthlyBudget(7L, month.atDay(1), TransactionCategory.DINING, new BigDecimal("20"))));
        var result = service().progress(7L, month);
        assertEquals(0, result.get(0).remaining().compareTo(new BigDecimal("-5")));
        assertEquals("OVER", result.get(0).status());
        assertEquals(0, result.get(1).spent().signum());
    }
    @Test void updatingBudgetReusesExistingEntity() {
        var existing = new MonthlyBudget(7L, month.atDay(1), TransactionCategory.DINING, new BigDecimal("10"));
        when(budgets.findByUserIdAndMonthStartAndCategory(7L, month.atDay(1), TransactionCategory.DINING)).thenReturn(Optional.of(existing));
        when(budgets.save(any())).thenAnswer(call -> call.getArgument(0));
        assertSame(existing, service().save(7L, month, TransactionCategory.DINING, new BigDecimal("25")));
        assertEquals(new BigDecimal("25"), existing.getLimitAmount());
    }
    @Test void createsBudgetForRequestedUserAndMonth() {
        when(budgets.findByUserIdAndMonthStartAndCategory(7L, month.atDay(1), TransactionCategory.DINING)).thenReturn(Optional.empty());
        when(budgets.save(any())).thenAnswer(call -> call.getArgument(0));
        var saved = service().save(7L, month, TransactionCategory.DINING, new BigDecimal("25"));
        assertEquals(7L, saved.getUserId());
        assertEquals(month.atDay(1), saved.getMonthStart());
    }
    @Test void removingMissingBudgetDoesNotDeleteAnything() {
        when(budgets.findByUserIdAndMonthStartAndCategory(7L, month.atDay(1), TransactionCategory.DINING)).thenReturn(Optional.empty());
        assertFalse(service().remove(7L, month, TransactionCategory.DINING));
        verify(budgets, never()).delete(any());
    }
    @Test void removesOnlyTheRequestedBudget() {
        var existing = new MonthlyBudget(7L, month.atDay(1), TransactionCategory.DINING, new BigDecimal("10"));
        when(budgets.findByUserIdAndMonthStartAndCategory(7L, month.atDay(1), TransactionCategory.DINING)).thenReturn(Optional.of(existing));
        assertTrue(service().remove(7L, month, TransactionCategory.DINING));
        verify(budgets).delete(existing);
        verifyNoInteractions(transactions);
    }
}
