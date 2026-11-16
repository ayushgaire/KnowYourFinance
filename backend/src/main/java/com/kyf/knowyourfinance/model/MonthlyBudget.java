package com.kyf.knowyourfinance.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A separate limit for one user, calendar month and spending category. */
@Entity
@Table(name = "monthly_budgets", uniqueConstraints = @UniqueConstraint(
        name = "uq_budget_user_month_category", columnNames = {"user_id", "month_start", "category"}))
public class MonthlyBudget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TransactionCategory category;
    @Column(name = "limit_amount", nullable = false, precision = 11, scale = 2)
    private BigDecimal limitAmount;

    protected MonthlyBudget() {}
    public MonthlyBudget(Long userId, LocalDate monthStart, TransactionCategory category, BigDecimal limitAmount) {
        this.userId = userId;
        this.monthStart = monthStart;
        this.category = category;
        this.limitAmount = limitAmount;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getMonthStart() { return monthStart; }
    public TransactionCategory getCategory() { return category; }
    public BigDecimal getLimitAmount() { return limitAmount; }
    public void setLimitAmount(BigDecimal amount) { limitAmount = amount; }
}
