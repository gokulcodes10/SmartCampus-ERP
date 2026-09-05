import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import api from "@/services/api";
import * as authService from "@/services/authService";

vi.mock("@/services/api", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
});

describe("authService", () => {
  it("login POSTs to /api/auth/login with the credentials and returns { token, user }", async () => {
    const payload = { token: "jwt", user: { id: 1, email: "a@b.com", fullName: "A", role: "STUDENT" as const, enabled: true, createdAt: "2026-01-01" } };
    (api.post as Mock).mockResolvedValue({ data: payload });

    const result = await authService.login({ email: "a@b.com", password: "secret123" });

    expect(api.post).toHaveBeenCalledWith("/api/auth/login", { email: "a@b.com", password: "secret123" });
    expect(result).toEqual(payload);
  });

  it("register POSTs to /api/auth/register and returns the created user", async () => {
    const user = { id: 2, email: "new@b.com", fullName: "New", role: "STUDENT" as const, enabled: true, createdAt: "2026-01-01" };
    (api.post as Mock).mockResolvedValue({ data: user });

    const result = await authService.register({ email: "new@b.com", password: "secret123", fullName: "New" });

    expect(api.post).toHaveBeenCalledWith("/api/auth/register", {
      email: "new@b.com",
      password: "secret123",
      fullName: "New",
    });
    expect(result).toEqual(user);
  });

  it("fetchCurrentUser GETs /api/auth/me", async () => {
    const user = { id: 1, email: "a@b.com", fullName: "A", role: "ADMIN" as const, enabled: true, createdAt: "2026-01-01" };
    (api.get as Mock).mockResolvedValue({ data: user });

    const result = await authService.fetchCurrentUser();

    expect(api.get).toHaveBeenCalledWith("/api/auth/me");
    expect(result).toEqual(user);
  });

  it("verifyOtp POSTs to /api/auth/password-reset/verify", async () => {
    (api.post as Mock).mockResolvedValue({ data: { message: "ok" } });

    await authService.verifyOtp({ email: "a@b.com", otp: "123456" });

    expect(api.post).toHaveBeenCalledWith("/api/auth/password-reset/verify", {
      email: "a@b.com",
      otp: "123456",
    });
  });

  it("resetPassword POSTs to /api/auth/password-reset/reset", async () => {
    (api.post as Mock).mockResolvedValue({ data: { message: "ok" } });

    await authService.resetPassword({ email: "a@b.com", otp: "123456", newPassword: "newpass1" });

    expect(api.post).toHaveBeenCalledWith("/api/auth/password-reset/reset", {
      email: "a@b.com",
      otp: "123456",
      newPassword: "newpass1",
    });
  });
});
