import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ManualEntry from "../components/ManualEntry";
import { addTransaction } from "../api";

vi.mock("../api", () => ({ addTransaction: vi.fn() }));

beforeEach(() => {
  vi.clearAllMocks();
  addTransaction.mockResolvedValue({ id: 1 });
});

function enterValidTransaction() {
  fireEvent.change(screen.getByRole("spinbutton", { name: /^Amount/ }), { target: { value: "12.50" } });
  fireEvent.change(screen.getByRole("textbox", { name: /^What was it\?/ }), { target: { value: "Farmers market" } });
  fireEvent.change(screen.getByLabelText(/^Date/), { target: { value: "2026-11-15" } });
}

function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Add transaction" }));
}

describe("manual transaction validation", () => {
  it("shows required-field errors and focuses the first invalid field without saving", () => {
    const onAdded = vi.fn();
    render(<ManualEntry onAdded={onAdded} />);
    fireEvent.change(screen.getByLabelText(/^Date/), { target: { value: "" } });

    submit();

    expect(addTransaction).not.toHaveBeenCalled();
    expect(onAdded).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toContain("Check the highlighted fields.");
    for (const message of ["Enter an amount.", "Describe what this was.", "Choose a date."]) {
      const error = screen.getByText(message);
      const field = document.querySelector(`[aria-describedby="${error.id}"]`);
      expect(field.getAttribute("aria-invalid")).toBe("true");
    }
    expect(document.activeElement).toBe(screen.getByRole("spinbutton", { name: /^Amount/ }));
  });

  it.each(["0", "-12.50", "0.001"])("rejects the nonpositive or sub-cent amount %s", (amount) => {
    render(<ManualEntry />);
    enterValidTransaction();
    fireEvent.change(screen.getByRole("spinbutton", { name: /^Amount/ }), { target: { value: amount } });

    submit();

    expect(addTransaction).not.toHaveBeenCalled();
    expect(screen.getByText("Enter an amount of at least $0.01.")).toBeTruthy();
  });

  it("rejects fractional cents to match the amount field's step", () => {
    render(<ManualEntry />);
    enterValidTransaction();
    fireEvent.change(screen.getByRole("spinbutton", { name: /^Amount/ }), { target: { value: "12.345" } });

    submit();

    expect(addTransaction).not.toHaveBeenCalled();
    expect(screen.getByText(/Enter an amount in whole cents/)).toBeTruthy();
  });

  it("rejects a whitespace-only description", () => {
    render(<ManualEntry />);
    enterValidTransaction();
    fireEvent.change(screen.getByRole("textbox", { name: /^What was it\?/ }), { target: { value: "   " } });

    submit();

    expect(addTransaction).not.toHaveBeenCalled();
    expect(screen.getByText("Describe what this was.")).toBeTruthy();
    expect(document.activeElement).toBe(screen.getByRole("textbox", { name: /^What was it\?/ }));
  });

  it.each(["Expense", "Income"])("saves a valid %s and clears the entry after success", async (type) => {
    const onAdded = vi.fn();
    render(<ManualEntry onAdded={onAdded} />);
    enterValidTransaction();
    fireEvent.click(screen.getByRole("radio", { name: type }));

    submit();

    await screen.findByText("Transaction added.");
    expect(addTransaction).toHaveBeenCalledExactlyOnceWith({
      date: "2026-11-15", description: "Farmers market", amount: "12.50", type: type.toUpperCase(), category: "OTHER",
    });
    expect(onAdded).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("spinbutton", { name: /^Amount/ }).value).toBe("");
    expect(screen.getByRole("textbox", { name: /^What was it\?/ }).value).toBe("");
  });

  it("allows the minimum one-cent amount after the user fixes validation errors", async () => {
    render(<ManualEntry />);
    submit();
    enterValidTransaction();
    fireEvent.change(screen.getByRole("spinbutton", { name: /^Amount/ }), { target: { value: "0.01" } });
    fireEvent.change(screen.getByRole("textbox", { name: /^What was it\?/ }), { target: { value: "  Cash purchase  " } });

    submit();

    await screen.findByText("Transaction added.");
    expect(addTransaction).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ amount: "0.01", description: "Cash purchase" }));
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByText("Enter an amount.")).toBeNull();
    expect(screen.getByRole("spinbutton", { name: /^Amount/ }).getAttribute("aria-invalid")).toBe("false");
  });

  it("preserves a failed entry and exposes server field errors for retry", async () => {
    addTransaction.mockRejectedValueOnce(Object.assign(new Error("Could not save."), {
      fieldErrors: { description: "Use a shorter description." },
    }));
    render(<ManualEntry />);
    enterValidTransaction();

    submit();

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("Could not save."));
    const fieldError = screen.getByText("Use a shorter description.");
    expect(screen.getByRole("textbox", { name: /^What was it\?/ }).getAttribute("aria-describedby")).toBe(fieldError.id);
    expect(screen.getByRole("spinbutton", { name: /^Amount/ }).value).toBe("12.50");
    fireEvent.change(screen.getByRole("textbox", { name: /^What was it\?/ }), { target: { value: "Market" } });

    submit();

    await screen.findByText("Transaction added.");
    expect(addTransaction).toHaveBeenCalledTimes(2);
    expect(screen.queryByText("Use a shorter description.")).toBeNull();
  });
});
