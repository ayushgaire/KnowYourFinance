package com.kyf.knowyourfinance.dto;

/** What a reset actually removed. */
public record ResetResult(long deletedTransactions, long deletedBudgets, boolean nameRemoved) {
}
