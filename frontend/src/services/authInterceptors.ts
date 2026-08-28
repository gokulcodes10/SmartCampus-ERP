import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from "axios";

import type { ErrorEnvelope } from "@/types/auth";
import { clearStoredToken, getStoredToken } from "@/utils/tokenStorage";

/**
 * Wires the shared axios instance (`services/api.ts`) for JWT auth, per the
 * Phase 2 checkpoint: attach the bearer token to every request, and on a 401
 * clear auth and send the user back to /login.
 *
 * NOT called automatically — `api.ts` is a shared file owned by the
 * integrator (see this agent's final report for the exact one-line wiring
 * to add there). Call once, e.g.:
 *
 *   import { setupAuthInterceptors } from "@/services/authInterceptors";
 *   setupAuthInterceptors(api);
 */
export function setupAuthInterceptors(instance: AxiosInstance): void {
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = getStoredToken();
    if (token) {
      config.headers.set("Authorization", `Bearer ${token}`);
    }
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    (error: AxiosError<ErrorEnvelope>) => {
      if (error.response?.status === 401 && !isPreAuthRequest(error.config?.url)) {
        clearStoredToken();
        notifyUnauthorized();
      }
      return Promise.reject(error);
    },
  );
}

/**
 * `/api/auth/me` is the only *authenticated* call under `/api/auth` in
 * Phase 2 — a 401 there means "your session is no longer valid". A 401 from
 * login/register/password-reset/* is instead an expected, form-level result
 * (e.g. bad credentials) that the page itself must display — it must NOT
 * trigger a global logout-redirect. Matches the real `AuthController` /
 * `PasswordResetController` paths (`/api/auth/password-reset/{request,verify,reset}`),
 * not the earlier assumed `/forgot-password`, `/verify-otp`, `/reset-password` shape.
 */
function isPreAuthRequest(url?: string): boolean {
  return !!url && /\/api\/auth\/(login|register|password-reset\/.+)(\?|$)/.test(url);
}

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

/**
 * `AuthContext` registers itself here on mount so a 401 clears its React
 * state and navigates via `react-router` — no full-page reload. Returns an
 * unsubscribe function for the `useEffect` cleanup.
 */
export function onUnauthorized(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

function notifyUnauthorized(): void {
  unauthorizedHandler?.();
}
