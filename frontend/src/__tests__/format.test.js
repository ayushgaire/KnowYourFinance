import { describe, expect, it } from "vitest";
import { categoryLabel, formatCurrency, formatDayCellCurrency, formatPercent } from "../utils/format";

describe("formatCurrency", () => {
  it("shows dollars with two decimals and thousands separators", () => {
    expect(formatCurrency(2450)).toBe("$2,450.00");
  });

  it("accepts numbers that arrive from the API as strings", () => {
    expect(formatCurrency("5.75")).toBe("$5.75");
  });

  it("puts the minus sign on negative savings", () => {
    expect(formatCurrency(-200)).toBe("-$200.00");
  });
});

describe("formatPercent", () => {
  it("rounds to one decimal place", () => {
    expect(formatPercent(12.345)).toBe("12.3%");
  });

  it("adds a plus sign only when asked and only for increases", () => {
    expect(formatPercent(5, { signed: true })).toBe("+5.0%");
    expect(formatPercent(-5, { signed: true })).toBe("-5.0%");
    expect(formatPercent(5)).toBe("5.0%");
  });
});

describe("categoryLabel", () => {
  it("turns backend enum names into readable labels", () => {
    expect(categoryLabel("RENT_MORTGAGE")).toBe("Rent Mortgage");
    expect(categoryLabel("GROCERIES")).toBe("Groceries");
  });

  it("falls back to Other when there is no category", () => {
    expect(categoryLabel(null)).toBe("Other");
    expect(categoryLabel(undefined)).toBe("Other");
  });
});

describe("formatDayCellCurrency", () => {
  it("shows whole dollars below a thousand", () => {
    expect(formatDayCellCurrency(4.5)).toBe("$5");
    expect(formatDayCellCurrency(150.06)).toBe("$150");
  });

  it("shortens thousands and millions", () => {
    expect(formatDayCellCurrency(1234.86)).toBe("$1.2K");
    expect(formatDayCellCurrency(999.95)).toBe("$1K");
    expect(formatDayCellCurrency(12999.99)).toBe("$13K");
    expect(formatDayCellCurrency(12345)).toBe("$12K");
    expect(formatDayCellCurrency(123456)).toBe("$123K");
    expect(formatDayCellCurrency(2500000)).toBe("$2.5M");
  });

  it("never shows a real expense as zero", () => {
    expect(formatDayCellCurrency(0.3)).toBe("<$1");
  });
});
