package com.kyf.knowyourfinance.dto;

import com.kyf.knowyourfinance.model.TransactionCategory;
import java.math.BigDecimal;

public record BudgetProgress(TransactionCategory category, BigDecimal limitAmount,
        BigDecimal spent, BigDecimal remaining, BigDecimal percentUsed, String status) {}
