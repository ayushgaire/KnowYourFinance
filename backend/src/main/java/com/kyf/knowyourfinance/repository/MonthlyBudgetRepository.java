package com.kyf.knowyourfinance.repository;

import com.kyf.knowyourfinance.model.MonthlyBudget;
import com.kyf.knowyourfinance.model.TransactionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {
    List<MonthlyBudget> findByUserIdAndMonthStartOrderByCategory(Long userId, LocalDate monthStart);
    Optional<MonthlyBudget> findByUserIdAndMonthStartAndCategory(Long userId, LocalDate monthStart, TransactionCategory category);
}
