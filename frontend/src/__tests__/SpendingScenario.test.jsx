import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import Dashboard from "../components/Dashboard";

const PANEL = "What if I hadn’t spent that?";

const report = (overrides = {}) => ({
  totalIncome: 3000, totalExpenses: 1000, netSavings: 2000,
  categoryBreakdown: [], insights: [], previousPeriodHasData: false,
  ...overrides,
});

// Answers each kind of request. A value of "fail" makes that request return an error.
function serve({ monthly = report(), range = report(), transactions = [] } = {}) {
  vi.stubGlobal("fetch", vi.fn(async (url) => {
    const address = String(url);
    const body = address.includes("/transactions") ? transactions
      : address.includes("/dashboard/report-range") ? range
        : address.includes("/dashboard/report") ? monthly : [];
    return body === "fail"
      ? { ok: false, status: 500, json: async () => ({}) }
      : { ok: true, status: 200, json: async () => body };
  }));
}

const expense = (id, date, amount, description) => ({ id, date, amount, description, type: "EXPENSE" });

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date(2026, 10, 25, 12));
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const openPanel = async () => {
  const panel = await screen.findByRole("region", { name: PANEL });
  fireEvent.click(within(panel).getByText("Compare without selected expenses"));
  return panel;
};

it("offers only the 8 largest expenses and says so", async () => {
  serve({ transactions: Array.from({ length: 10 }, (_, i) => expense(i + 1, "2026-11-10", (i + 1) * 10, `Item ${i + 1}`)) });
  render(<Dashboard refreshKey={0} />);

  const panel = await openPanel();
  expect(within(panel).getByRole("group", { name: "Up to 8 largest expenses in this period" })).toBeTruthy();
  expect(within(panel).getAllByRole("checkbox")).toHaveLength(8);
  expect(within(panel).queryByRole("checkbox", { name: /Item 2 ·/ })).toBeNull();
  expect(within(panel).getByRole("checkbox", { name: /Item 10 ·/ })).toBeTruthy();
});

it("stays hidden when the report cannot be loaded", async () => {
  serve({ monthly: "fail", transactions: [expense(1, "2026-11-10", 600, "Car repair")] });
  render(<Dashboard refreshKey={0} />);

  expect((await screen.findByRole("alert")).textContent).toBe("Failed to load report (500)");
  expect(screen.queryByRole("region", { name: PANEL })).toBeNull();
});

it("stays hidden when the transaction list cannot be loaded, while the totals still show", async () => {
  serve({ transactions: "fail" });
  render(<Dashboard refreshKey={0} />);

  expect(await screen.findByText("Total Expenses")).toBeTruthy();
  // The recent transactions list settles to empty once the failed request is handled.
  expect(await screen.findByText("No transactions yet.")).toBeTruthy();
  expect(screen.queryByRole("region", { name: PANEL })).toBeNull();
});

it("disappears when a newly chosen period fails to load, instead of mixing old totals with new dates", async () => {
  serve({ range: "fail", transactions: [expense(1, "2026-11-10", 600, "Car repair")] });
  render(<Dashboard refreshKey={0} />);
  await screen.findByRole("region", { name: PANEL });

  fireEvent.click(screen.getByRole("button", { name: "Custom range" }));

  expect(await screen.findByRole("alert")).toBeTruthy();
  expect(screen.queryByRole("region", { name: PANEL })).toBeNull();
});

it("uses the expenses and totals of a custom date range", async () => {
  serve({
    range: report({ totalIncome: 500, totalExpenses: 300, netSavings: 200 }),
    transactions: [
      expense(1, "2026-09-30", 900, "Before the range"),
      expense(2, "2026-10-01", 120, "First day"),
      expense(3, "2026-10-15", 80, "Last day"),
      expense(4, "2026-10-16", 700, "After the range"),
    ],
  });
  render(<Dashboard refreshKey={0} />);
  fireEvent.click(await screen.findByRole("button", { name: "Custom range" }));
  fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-10-01" } });
  fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-10-15" } });

  const panel = await openPanel();
  expect(within(panel).getAllByRole("checkbox")).toHaveLength(2);
  fireEvent.click(within(panel).getByRole("checkbox", { name: /First day/ }));
  expect(within(panel).getByText(/Expenses: \$300\.00 recorded · \$180\.00/)).toBeTruthy();
  expect(within(panel).getByText(/Net savings: \$200\.00 recorded · \$320\.00/)).toBeTruthy();
});

it("is not shown for a period with income but no expenses", async () => {
  serve({
    monthly: report({ totalExpenses: 0, netSavings: 3000 }),
    transactions: [
      { id: 1, date: "2026-11-01", amount: 3000, description: "Payroll", type: "INCOME" },
      expense(2, "2026-10-10", 600, "Last month's repair"),
    ],
  });
  render(<Dashboard refreshKey={0} />);

  // Waiting for the recent list shows the transactions have arrived.
  expect(await screen.findByText("Payroll")).toBeTruthy();
  expect(screen.getByText("Total Expenses")).toBeTruthy();
  expect(screen.queryByRole("region", { name: PANEL })).toBeNull();
});
