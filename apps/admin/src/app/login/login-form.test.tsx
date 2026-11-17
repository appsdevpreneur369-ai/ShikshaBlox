import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(cleanup);
import { LoginForm } from "./login-form";

vi.mock("./actions", () => ({
  passwordLogin: vi.fn(async () => ({})),
  requestCode: vi.fn(async () => ({})),
  verifyCode: vi.fn(async () => ({})),
}));

describe("LoginForm", () => {
  it("has labelled email and password fields", () => {
    render(<LoginForm />);
    expect(screen.getByLabelText("Email")).toBeDefined();
    expect(screen.getByLabelText("Password")).toBeDefined();
    expect(screen.getByRole("button", { name: "Sign in" })).toBeDefined();
  });

  it("switches to the email-code method", () => {
    render(<LoginForm />);
    fireEvent.click(screen.getByRole("tab", { name: "Email code" }));
    expect(screen.queryByLabelText("Password")).toBeNull();
    expect(screen.getByRole("button", { name: "Email me a code" })).toBeDefined();
  });

  it("shows an initial error accessibly", () => {
    render(<LoginForm initialError="That sign-in step expired." />);
    expect(screen.getByRole("alert").textContent).toContain("expired");
  });
});
