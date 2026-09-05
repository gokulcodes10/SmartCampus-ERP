import { MemoryRouter } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import { AuthProvider } from "@/context/AuthContext";
import { useAuth } from "@/hooks/useAuth";
import * as authService from "@/services/authService";
import { getStoredToken, setStoredToken } from "@/utils/tokenStorage";
import type { AuthenticatedUser } from "@/types/auth";

vi.mock("@/services/authService");

const mockUser: AuthenticatedUser = {
  id: 1,
  email: "student@example.com",
  fullName: "Ada Lovelace",
  role: "STUDENT",
  enabled: true,
  createdAt: "2026-01-01T00:00:00Z",
};

/** Exposes AuthContext state as text so tests can assert on it without reaching into internals. */
function Consumer() {
  const { user, isLoading, isAuthenticated, login, logout } = useAuth();
  return (
    <div>
      <p>loading: {String(isLoading)}</p>
      <p>authenticated: {String(isAuthenticated)}</p>
      <p>user: {user?.email ?? "none"}</p>
      <button onClick={() => login("student@example.com", "password123").catch(() => {})}>
        do-login
      </button>
      <button onClick={logout}>do-logout</button>
    </div>
  );
}

function renderAuth() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});

afterEach(() => {
  localStorage.clear();
});

describe("AuthProvider bootstrap", () => {
  it("with no stored token: settles isLoading false and never calls GET /api/auth/me", async () => {
    renderAuth();

    // isLoading is initialized false during render (the fixed behavior) rather
    // than being corrected a tick later inside an effect.
    expect(screen.getByText("loading: false")).toBeInTheDocument();
    expect(screen.getByText("user: none")).toBeInTheDocument();

    await waitFor(() => expect(authService.fetchCurrentUser).not.toHaveBeenCalled());
  });

  it("with a stored token: calls GET /api/auth/me and sets the user", async () => {
    setStoredToken("stored-jwt");
    (authService.fetchCurrentUser as Mock).mockResolvedValue(mockUser);

    renderAuth();
    expect(screen.getByText("loading: true")).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText("loading: false")).toBeInTheDocument());
    expect(authService.fetchCurrentUser).toHaveBeenCalledTimes(1);
    expect(screen.getByText("user: student@example.com")).toBeInTheDocument();
    expect(screen.getByText("authenticated: true")).toBeInTheDocument();
  });

  it("a failed GET /api/auth/me clears the stored token", async () => {
    setStoredToken("stale-jwt");
    (authService.fetchCurrentUser as Mock).mockRejectedValue(new Error("401"));

    renderAuth();
    await waitFor(() => expect(screen.getByText("loading: false")).toBeInTheDocument());

    expect(getStoredToken()).toBeNull();
    expect(screen.getByText("authenticated: false")).toBeInTheDocument();
  });
});

describe("login / logout", () => {
  it("login stores the token and sets the user", async () => {
    (authService.login as Mock).mockResolvedValue({ token: "fresh-jwt", user: mockUser });
    renderAuth();
    await waitFor(() => expect(screen.getByText("loading: false")).toBeInTheDocument());

    await userEvent.click(screen.getByText("do-login"));

    await waitFor(() => expect(screen.getByText("user: student@example.com")).toBeInTheDocument());
    expect(getStoredToken()).toBe("fresh-jwt");
  });

  it("logout clears both the token and the user", async () => {
    setStoredToken("stored-jwt");
    (authService.fetchCurrentUser as Mock).mockResolvedValue(mockUser);
    renderAuth();
    await waitFor(() => expect(screen.getByText("user: student@example.com")).toBeInTheDocument());

    await userEvent.click(screen.getByText("do-logout"));

    expect(getStoredToken()).toBeNull();
    expect(screen.getByText("user: none")).toBeInTheDocument();
  });
});
