import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import LoginPage from "@/pages/auth/LoginPage";
import { useAuth } from "@/hooks/useAuth";

vi.mock("@/hooks/useAuth", () => ({ useAuth: vi.fn() }));

const mockLogin = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useAuth as Mock).mockReturnValue({ login: mockLogin });
});

function renderPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe("LoginPage form validation", () => {
  it("shows inline errors for empty submission and does not call login", async () => {
    renderPage();

    await userEvent.click(screen.getByRole("button", { name: /log in/i }));

    expect(await screen.findByText("Email is required.")).toBeInTheDocument();
    expect(screen.getByText("Password is required.")).toBeInTheDocument();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it("shows an inline error for a malformed email and does not call login", async () => {
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), "not-an-email");
    await userEvent.type(screen.getByLabelText(/^password$/i), "somepassword");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));

    expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it("submits with valid input", async () => {
    mockLogin.mockResolvedValue({ id: 1, role: "STUDENT" });
    renderPage();

    await userEvent.type(screen.getByLabelText(/email/i), "student@example.com");
    await userEvent.type(screen.getByLabelText(/^password$/i), "password123");
    await userEvent.click(screen.getByRole("button", { name: /log in/i }));

    expect(mockLogin).toHaveBeenCalledWith("student@example.com", "password123");
  });
});
