import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import Dashboard from "../components/Dashboard";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

it("shows recorded expenses by day for the selected month", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2024, 1, 29, 12));
  const report = { totalIncome: 200, totalExpenses: 35, netSavings: 165, categoryBreakdown: [] };
  const transactions = [
    { id: 1, date: "2024-02-29", type: "EXPENSE", amount: "10.00", description: "Transit" },
    { id: 2, date: "2024-02-29", type: "EXPENSE", amount: 25, description: "Groceries" },
    { id: 3, date: "2024-02-29", type: "INCOME", amount: 200, description: "Payroll" },
    { id: 4, date: "2024-01-29", type: "EXPENSE", amount: 100, description: "January charge" },
  ];
  vi.stubGlobal("fetch", vi.fn(async (url) => ({
    ok: true,
    json: async () => String(url).includes("/transactions") ? transactions
      : String(url).includes("/dashboard/report") ? report : [],
  })));
  render(<Dashboard refreshKey={0} />);

  const leapDay = await screen.findByRole("button", { name: "February 29, $35.00" });
  fireEvent.click(leapDay);
  expect(leapDay.getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByText("Transit · $10.00")).toBeTruthy();
  expect(screen.getByText("Groceries · $25.00")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "February 28, no expenses recorded" }));
  expect(screen.getByText("No expenses recorded for this day.")).toBeTruthy();
  expect(screen.queryByText("Transit · $10.00")).toBeNull();
});

// Renders the dashboard on 15 March 2026 with the given report and transactions.
function renderMarch(transactions, report = { totalIncome: 200, totalExpenses: 0, netSavings: 200, categoryBreakdown: [] }) {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 2, 15, 12));
  vi.stubGlobal("fetch", vi.fn(async (url) => ({
    ok: true,
    json: async () => String(url).includes("/transactions") ? transactions
      : String(url).includes("/dashboard/report") ? report : [],
  })));
  render(<Dashboard refreshKey={0} />);
}

it("shows every day as empty for a month with no transactions", async () => {
  renderMarch([{ id: 1, date: "2026-02-10", type: "EXPENSE", amount: 40, description: "February charge" }]);

  await screen.findByRole("heading", { name: "Spending calendar" });
  expect(screen.getAllByRole("button", { name: /^March \d+, no expenses recorded$/ })).toHaveLength(31);
  fireEvent.click(screen.getByRole("button", { name: "March 10, no expenses recorded" }));
  expect(screen.getByText("No expenses recorded for this day.")).toBeTruthy();
});

it("shows the empty state instead of a calendar when the month has nothing at all", async () => {
  renderMarch([], { totalIncome: 0, totalExpenses: 0, netSavings: 0, categoryBreakdown: [] });

  expect(await screen.findByText("No transactions yet")).toBeTruthy();
  expect(screen.queryByRole("heading", { name: "Spending calendar" })).toBeNull();
});

it("is hidden on a custom date range and returns for a single month", async () => {
  renderMarch([{ id: 1, date: "2026-03-02", type: "EXPENSE", amount: 40, description: "March charge" }]);
  await screen.findByRole("heading", { name: "Spending calendar" });

  fireEvent.click(screen.getByRole("button", { name: "Custom range" }));
  // Wait for the range report to arrive, so the calendar is not just hidden by loading.
  expect(await screen.findByText("Total Income")).toBeTruthy();
  expect(screen.queryByRole("heading", { name: "Spending calendar" })).toBeNull();

  fireEvent.click(screen.getByRole("button", { name: "This month" }));
  expect(await screen.findByRole("heading", { name: "Spending calendar" })).toBeTruthy();
});

it("adds a day's amounts in cents and offers a short form for narrow screens", async () => {
  renderMarch([
    { id: 1, date: "2026-03-02", type: "EXPENSE", amount: "0.10", description: "Gum" },
    { id: 2, date: "2026-03-02", type: "EXPENSE", amount: "0.20", description: "Mint" },
    { id: 3, date: "2026-03-03", type: "EXPENSE", amount: 1234.86, description: "Rent" },
  ]);

  const small = await screen.findByRole("button", { name: "March 2, $0.30" });
  expect(small.textContent).toContain("<$1");
  const large = screen.getByRole("button", { name: "March 3, $1,234.86" });
  expect(large.textContent).toContain("$1,234.86");
  expect(large.textContent).toContain("$1.2K");
});
