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
