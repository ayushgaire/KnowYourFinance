import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import ThemeToggle from "../components/ThemeToggle";
import { getInitialTheme } from "../utils/theme";

beforeEach(() => {
  localStorage.clear();
  document.documentElement.removeAttribute("data-theme");
});
afterEach(() => cleanup());

describe("theme", () => {
  it("uses the saved choice first", () => {
    localStorage.setItem("kyf-theme", "dark");
    expect(getInitialTheme()).toBe("dark");
  });

  it("ignores an unknown saved value", () => {
    localStorage.setItem("kyf-theme", "purple");
    expect(["light", "dark"]).toContain(getInitialTheme());
  });

  it("switches to dark, saves it, and switches back", () => {
    localStorage.setItem("kyf-theme", "light");
    render(<ThemeToggle />);
    const button = screen.getByRole("button", { name: "Dark mode" });
    expect(button.getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(button);
    expect(document.documentElement.getAttribute("data-theme")).toBe("dark");
    expect(localStorage.getItem("kyf-theme")).toBe("dark");
    expect(button.getAttribute("aria-pressed")).toBe("true");

    fireEvent.click(button);
    expect(document.documentElement.getAttribute("data-theme")).toBe("light");
    expect(localStorage.getItem("kyf-theme")).toBe("light");
  });
});
