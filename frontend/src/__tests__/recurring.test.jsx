import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import RecurringBills from "../components/RecurringBills";
import { addOneMonth, detectRecurring, dueLabel, monthlyTotal, normalizeDescription } from "../utils/recurring";

afterEach(() => cleanup());

const tx = (date, description, amount, type = "EXPENSE", category = "UTILITIES") => ({ date, description, amount, type, category });

const rentThreeMonths = [
  tx("2026-02-03", "RENT PAYMENT", 950, "EXPENSE", "RENT_MORTGAGE"),
  tx("2026-03-03", "RENT PAYMENT", 950, "EXPENSE", "RENT_MORTGAGE"),
  tx("2026-04-03", "RENT PAYMENT", 950, "EXPENSE", "RENT_MORTGAGE"),
];

describe("detectRecurring", () => {
  it("finds a charge that repeats in three months and predicts the next one", () => {
    const [bill] = detectRecurring(rentThreeMonths);
    expect(bill.description).toBe("RENT PAYMENT");
    expect(bill.amount).toBe(950);
    expect(bill.count).toBe(3);
    expect(bill.lastDate).toBe("2026-04-03");
    expect(bill.nextDate).toBe("2026-05-03");
  });

  it("needs at least three different months", () => {
    expect(detectRecurring(rentThreeMonths.slice(0, 2))).toEqual([]);
  });

  it("ignores income", () => {
    const pay = rentThreeMonths.map((t) => ({ ...t, type: "INCOME" }));
    expect(detectRecurring(pay)).toEqual([]);
  });

  it("allows small amount changes but not large ones", () => {
    const small = [tx("2026-02-10", "INTERNET BILL", 60), tx("2026-03-10", "INTERNET BILL", 62), tx("2026-04-10", "INTERNET BILL", 58)];
    expect(detectRecurring(small)).toHaveLength(1);
    const big = [tx("2026-02-10", "KROGER", 40), tx("2026-03-10", "KROGER", 140), tx("2026-04-10", "KROGER", 90)];
    expect(detectRecurring(big)).toEqual([]);
  });

  it("rejects things bought many times a month", () => {
    const frequent = [
      tx("2026-02-01", "COFFEE", 5), tx("2026-02-08", "COFFEE", 5), tx("2026-03-01", "COFFEE", 5),
      tx("2026-03-08", "COFFEE", 5), tx("2026-04-01", "COFFEE", 5),
    ];
    expect(detectRecurring(frequent)).toEqual([]);
  });

  it("treats the same merchant with different reference numbers as one bill", () => {
    const refs = [tx("2026-02-05", "NETFLIX 1111", 15), tx("2026-03-05", "Netflix [REDACTED]", 15), tx("2026-04-05", "NETFLIX 3333", 15)];
    expect(detectRecurring(refs)).toHaveLength(1);
  });

  it("lists the biggest bill first and totals them", () => {
    const internet = [tx("2026-02-10", "INTERNET BILL", 60), tx("2026-03-10", "INTERNET BILL", 60), tx("2026-04-10", "INTERNET BILL", 60)];
    const bills = detectRecurring([...internet, ...rentThreeMonths]);
    expect(bills.map((b) => b.description)).toEqual(["RENT PAYMENT", "INTERNET BILL"]);
    expect(monthlyTotal(bills)).toBe(1010);
  });

  it("copes with empty or odd input", () => {
    expect(detectRecurring(undefined)).toEqual([]);
    expect(detectRecurring([{ type: "EXPENSE", description: "X", amount: "abc", date: "2026-01-01" }])).toEqual([]);
  });
});

describe("dates and labels", () => {
  it("clamps the next date to the end of a shorter month", () => {
    expect(addOneMonth("2026-01-31")).toBe("2026-02-28");
    expect(addOneMonth("2026-12-15")).toBe("2027-01-15");
  });

  it("describes how soon a bill is due", () => {
    expect(dueLabel("2026-05-03", "2026-05-03")).toBe("Due today");
    expect(dueLabel("2026-05-04", "2026-05-03")).toBe("Due tomorrow");
    expect(dueLabel("2026-05-08", "2026-05-03")).toBe("Due in 5 days");
    expect(dueLabel("2026-06-03", "2026-05-03")).toBe("Next 2026-06-03");
    expect(dueLabel("2026-05-01", "2026-05-03")).toBe("Expected 2026-05-01");
  });

  it("normalizes descriptions", () => {
    expect(normalizeDescription("Netflix #1234 [REDACTED]")).toBe("NETFLIX");
  });
});

describe("RecurringBills panel", () => {
  it("shows bills with their monthly total", () => {
    render(<RecurringBills bills={detectRecurring(rentThreeMonths)} today="2026-04-30" />);
    expect(screen.getByText("RENT PAYMENT")).toBeTruthy();
    expect(screen.getByText(/About \$950\.00 a month/)).toBeTruthy();
    expect(screen.getByText("Due in 3 days")).toBeTruthy();
  });

  it("explains what to do when nothing repeats yet", () => {
    render(<RecurringBills bills={[]} today="2026-04-30" />);
    expect(screen.getByText(/three different months/)).toBeTruthy();
  });
});
