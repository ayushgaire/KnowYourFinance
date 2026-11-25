import { categoryLabel, formatCurrency } from "../utils/format";
import { daysBetween, dueLabel, monthlyTotal } from "../utils/recurring";

/** Dashboard panel listing the bills that repeat every month. */
export default function RecurringBills({ bills, today }) {
  return (
    <section className="panel recurring" aria-labelledby="recurring-title">
      <div className="panel-header">
        <h2 className="panel-title" id="recurring-title">Recurring bills</h2>
        {bills.length > 0 && (
          <p className="recurring__total">About {formatCurrency(monthlyTotal(bills))} a month</p>
        )}
      </div>

      {bills.length === 0 ? (
        <p className="muted chart-empty">
          No repeating bills found yet. A bill shows up here once the same charge
          appears in three different months.
        </p>
      ) : (
        <ul className="recent-list">
          {bills.map((bill) => {
            const soon = daysBetween(today, bill.nextDate) <= 7;
            return (
              <li key={bill.key} className="recent-list__item">
                <div className="recent-list__main">
                  <span className="recent-list__desc">{bill.description}</span>
                  <span className="recent-list__meta">
                    {categoryLabel(bill.category)} · seen in {bill.count} months
                  </span>
                </div>
                <div className="recurring__right">
                  <span className="amount amount--expense">{formatCurrency(bill.amount)}</span>
                  <span className={`recurring__due${soon ? " recurring__due--soon" : ""}`}>
                    {dueLabel(bill.nextDate, today)}
                  </span>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
