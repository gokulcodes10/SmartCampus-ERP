import { createContext, useCallback, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";

import * as authService from "@/services/authService";
import { onUnauthorized } from "@/services/authInterceptors";
import type { AuthenticatedUser } from "@/types/auth";
import { clearStoredToken, getStoredToken, setStoredToken } from "@/utils/tokenStorage";

export interface AuthContextValue {
  /** The authenticated user, or null while logged out / not yet bootstrapped. */
  user: AuthenticatedUser | null;
  /** True only while bootstrapping a stored token via GET /api/auth/me on load. */
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<AuthenticatedUser>;
  /** Self-registration is STUDENT-only (G1) — there is no role parameter. */
  registerStudent: (email: string, password: string, fullName: string) => Promise<AuthenticatedUser>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();

  const clearAuth = useCallback(() => {
    setUser(null);
    clearStoredToken();
  }, []);

  // A 401 from any authenticated call (routed through services/api.ts) clears
  // auth and returns to /login — see services/authInterceptors.ts.
  useEffect(() => {
    return onUnauthorized(() => {
      clearAuth();
      navigate("/login", { replace: true });
    });
  }, [clearAuth, navigate]);

  // Bootstrap: a stored token is re-validated against GET /api/auth/me on
  // load rather than trusted blindly, since it may have expired or been
  // revoked (user disabled) since it was last used.
  useEffect(() => {
    let cancelled = false;
    if (!getStoredToken()) {
      setIsLoading(false);
      return;
    }
    authService
      .fetchCurrentUser()
      .then((me) => {
        if (!cancelled) setUser(me);
      })
      .catch(() => {
        if (!cancelled) clearAuth();
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [clearAuth]);

  const login = useCallback(async (email: string, password: string) => {
    const { token, user: loggedInUser } = await authService.login({ email, password });
    setStoredToken(token);
    setUser(loggedInUser);
    return loggedInUser;
  }, []);

  const registerStudent = useCallback(
    async (email: string, password: string, fullName: string) =>
      authService.register({ email, password, fullName }),
    [],
  );

  const logout = useCallback(() => {
    clearAuth();
    navigate("/login", { replace: true });
  }, [clearAuth, navigate]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isLoading,
      isAuthenticated: user !== null,
      login,
      registerStudent,
      logout,
    }),
    [user, isLoading, login, registerStudent, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
