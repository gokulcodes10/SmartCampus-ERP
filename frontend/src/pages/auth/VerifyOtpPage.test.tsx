import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import VerifyOtpPage from "@/pages/auth/VerifyOtpPage";
import * as authService from "@/services/authService";
import { OTP_LENGTH } from "@/utils/validation";

vi.mock("@/services/authService");

beforeEach(() => {
  vi.clearAllMocks();
});

function renderWithEmail() {
  return render(
    <MemoryRouter initialEntries={[{ pathname: "/verify-otp", state: { email: "ada@example.com" } }]}>
      <VerifyOtpPage />
    </MemoryRouter>,
  );
}

describe("VerifyOtpPage form validation", () => {
  it("shows the fallback screen and never renders the form when no email was carried forward", () => {
    render(
      <MemoryRouter initialEntries={["/verify-otp"]}>
        <VerifyOtpPage />
      </MemoryRouter>,
    );
    expect(screen.queryByLabelText(/verification code/i)).not.toBeInTheDocument();
    expect(screen.getByText(/couldn't find which account/i)).toBeInTheDocument();
  });

  it("rejects an empty code and does not call verifyOtp", async () => {
    renderWithEmail();

    await userEvent.click(screen.getByRole("button", { name: /verify code/i }));

    expect(
      await screen.findByText(`Enter the ${OTP_LENGTH}-digit code sent to your email.`),
    ).toBeInTheDocument();
    expect(authService.verifyOtp).not.toHaveBeenCalled();
  });

  it("rejects a code shorter than the required length", async () => {
    renderWithEmail();

    await userEvent.type(screen.getByLabelText(/verification code/i), "123");
    await userEvent.click(screen.getByRole("button", { name: /verify code/i }));

    expect(
      await screen.findByText(`Enter the ${OTP_LENGTH}-digit code sent to your email.`),
    ).toBeInTheDocument();
    expect(authService.verifyOtp).not.toHaveBeenCalled();
  });

  it("strips non-digit characters as the user types", async () => {
    renderWithEmail();

    await userEvent.type(screen.getByLabelText(/verification code/i), "12a3b4");

    expect(screen.getByLabelText(/verification code/i)).toHaveValue("1234");
  });

  it("submits a well-formed code", async () => {
    (authService.verifyOtp as Mock).mockResolvedValue({ message: "ok" });
    renderWithEmail();

    await userEvent.type(screen.getByLabelText(/verification code/i), "1".repeat(OTP_LENGTH));
    await userEvent.click(screen.getByRole("button", { name: /verify code/i }));

    expect(authService.verifyOtp).toHaveBeenCalledWith({
      email: "ada@example.com",
      otp: "1".repeat(OTP_LENGTH),
    });
  });
});
