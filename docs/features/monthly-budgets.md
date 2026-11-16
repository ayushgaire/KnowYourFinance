# Monthly category budgets

This optional enhancement adds the budget functionality deferred from the original MVP. The original milestone requirements remain historical records.

Open **Budgets**, choose a month, select a spending category and save a positive USD limit. Each category can have one limit per month. Choose **Edit limit** to change it. **Remove** asks for confirmation and removes only the limit, never transactions.

## Spending and alerts

- Only `EXPENSE` transactions in the selected calendar month and user count, including imported and manually entered records. Uncategorized expenses count as Other. Income is excluded.
- Progress is calculated from current database records when Budgets loads or a limit changes. Navigating back to Budgets after transaction changes fetches new totals.
- Below 80%: On track. At 80%: Approaching limit. Exactly 100%: Limit reached. Above 100%: Over budget.
- Status comparisons use exact BigDecimal amounts; displayed percentages are rounded. Progress bars cap visually at 100%; the text retains the actual percentage and overspend.
- Summary totals cover only categories with saved limits, not all monthly spending. One category can exceed its limit even when the combined total remains under budget.
- Limits are month-specific; they do not roll over or repeat automatically. Alerts appear inside Budgets, not as email or push notifications.

## API

All endpoints follow the existing placeholder `userId` convention; the application still has no authentication. The frontend uses user 1.

- `GET /api/budgets?userId=1&year=2026&month=11`: category, limitAmount, spent, remaining, percentUsed and status for each saved limit.
- `PUT /api/budgets/DINING?userId=1&year=2026&month=11` with JSON `{"limitAmount": 300.00}` creates or updates that category's limit.
- `DELETE /api/budgets/DINING?userId=1&year=2026&month=11` removes that limit (204; 404 when absent).

Validation rejects non-positive limits, more than two decimal places, more than nine integer digits, Income categories, invalid users and invalid months. Years 1900–9999 are accepted. Duplicate simultaneous creates return a reload-and-retry conflict rather than creating duplicate budgets.

## Storage and review

`monthly_budgets` stores the user, first day of the month, category and decimal limit. A database unique constraint protects `(user_id, month_start, category)`. The existing `spring.jpa.hibernate.ddl-auto=update` configuration creates this additive table when the new backend starts; no existing transaction table or data is replaced. A deployment that disables automatic schema updates must create the table before using this feature.

## Validation

Frontend tests cover navigation, save/edit/remove, alerts, month isolation and stale responses, failed requests, and API parameters. Backend tests cover exact expense aggregation, month boundaries (including leap years), thresholds, update identity, user scoping, removal and input validation.

An isolated Docker/PostgreSQL instance was also used to check the real API and browser flow without modifying the existing local database. Standard backend integration/performance tests that require Docker inside the Maven test container are skipped in that test environment; real API/database checks cover the new budget flow separately.
