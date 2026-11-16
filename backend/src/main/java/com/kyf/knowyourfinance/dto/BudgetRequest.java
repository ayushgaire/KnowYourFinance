package com.kyf.knowyourfinance.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BudgetRequest(
        @NotNull @DecimalMin(value = "0.01", message = "Enter a limit of at least 0.01.")
        @Digits(integer = 9, fraction = 2, message = "Use up to 9 digits and 2 decimal places.")
        BigDecimal limitAmount) {}
