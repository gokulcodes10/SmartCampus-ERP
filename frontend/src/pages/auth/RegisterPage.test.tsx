import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import RegisterPage from "@/pages/auth/RegisterPage";
import { useAuth } from "@/hooks/useAuth";
import { MIN_PASSWORD_LENGTH } from "@/utils/validation";

vi.mock("@/hooks/useAuth", () => ({ useAuth: vi.fn() }));

const mockRegisterStudent = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useAuth as Mock).mockReturnValue({ registerStudent: mockRegisterStudent });
});

function renderPage() {
  return render(
    <MemoryRouter>
      <RegisterPage />
    </MemoryRouter>,
  );
}

describe("RegisterPage form validation", () => {
  it("shows inline errors for empty submission and does not register", async () => {
    renderPage();

    await userEvent.click(screen.getByRole("button", { name: /create account/i }));

    expect(await screen.findByText("Full name is required.")).toBeInTheDocument();
    expect(screen.getByText("Email is required.")).toBeInTheDocument();
    expect(screen.getByText("Password is required.")).toBeInTheDocument();
    expect(mockRegisterStudent).not.toHaveBeenCalled();
  });

  it("rejects a password shorter than the minimum length", async () => {
    renderPage();

    await userEvent.type(screen.getByLabelText(/full name/i), "Ada Lovelace");
    await userEvent.type(screen.getByLabelText(/email/i), "ada@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "short");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "short");
    await userEvent.click(screen.getByRole("button", { name: /create account/i }));

    expect(
      await screen.findByText(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`),
    ).toBeInTheDocument();
    expect(mockRegisterStudent).not.toHaveBeenCalled();
  });

  it("rejects a mismatched confirm-password", async () => {
    renderPage();

    await userEvent.type(screen.getByLabelText(/full name/i), "Ada Lovelace");
    await userEvent.type(screen.getByLabelText(/email/i), "ada@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password124");
    await userEvent.click(screen.getByRole("button", { name: /create account/i }));

    expect(await screen.findByText("Passwords do not match.")).toBeInTheDocument();
    expect(mockRegisterStudent).not.toHaveBeenCalled();
  });

  it("submits with valid, matching input", async () => {
    mockRegisterStudent.mockResolvedValue({ id: 1 });
    renderPage();

    await userEvent.type(screen.getByLabelText(/full name/i), "Ada Lovelace");
    await userEvent.type(screen.getByLabelText(/email/i), "ada@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "password123");
    await userEvent.type(screen.getByLabelText(/confirm password/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /create account/i }));

    expect(mockRegisterStudent).toHaveBeenCalledWith("ada@example.com", "password123", "Ada Lovelace");
  });
});
