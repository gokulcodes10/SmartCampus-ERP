import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import ResetPasswordPage from "@/pages/auth/ResetPasswordPage";
import * as authService from "@/services/authService";
import { MIN_PASSWORD_LENGTH } from "@/utils/validation";

vi.mock("@/services/authService");

beforeEach(() => {
  vi.clearAllMocks();
});

function renderWithState() {
  return render(
    <MemoryRouter
      initialEntries={[{ pathname: "/reset-password", state: { email: "ada@example.com", otp: "123456" } }]}
    >
      <ResetPasswordPage />
    </MemoryRouter>,
  );
}

describe("ResetPasswordPage form validation", () => {
  it("shows the fallback screen when no email/otp was carried forward", () => {
    render(
      <MemoryRouter initialEntries={["/reset-password"]}>
        <ResetPasswordPage />
      </MemoryRouter>,
    );
    expect(screen.queryByLabelText(/new password/i)).not.toBeInTheDocument();
    expect(screen.getByText(/verification session has expired/i)).toBeInTheDocument();
  });

  it("shows inline errors for empty submission and does not call resetPassword", async () => {
    renderWithState();

    await userEvent.click(screen.getByRole("button", { name: /reset password/i }));

    expect(await screen.findByText("Password is required.")).toBeInTheDocument();
    expect(authService.resetPassword).not.toHaveBeenCalled();
  });

  it("rejects a password shorter than the minimum length", async () => {
    renderWithState();

    await userEvent.type(screen.getByLabelText(/^new password$/i), "short");
    await userEvent.type(screen.getByLabelText(/confirm new password/i), "short");
    await userEvent.click(screen.getByRole("button", { name: /reset password/i }));

    expect(
      await screen.findByText(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`),
    ).toBeInTheDocument();
    expect(authService.resetPassword).not.toHaveBeenCalled();
  });

  it("rejects mismatched passwords", async () => {
    renderWithState();

    await userEvent.type(screen.getByLabelText(/^new password$/i), "password123");
    await userEvent.type(screen.getByLabelText(/confirm new password/i), "password124");
    await userEvent.click(screen.getByRole("button", { name: /reset password/i }));

    expect(await screen.findByText("Passwords do not match.")).toBeInTheDocument();
    expect(authService.resetPassword).not.toHaveBeenCalled();
  });

  it("submits with matching, valid passwords", async () => {
    (authService.resetPassword as Mock).mockResolvedValue({ message: "ok" });
    renderWithState();

    await userEvent.type(screen.getByLabelText(/^new password$/i), "password123");
    await userEvent.type(screen.getByLabelText(/confirm new password/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /reset password/i }));

    expect(authService.resetPassword).toHaveBeenCalledWith({
      email: "ada@example.com",
      otp: "123456",
      newPassword: "password123",
    });
  });
});
