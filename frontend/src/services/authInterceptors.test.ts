import axios, { AxiosError } from "axios";
import type { AxiosAdapter } from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { onUnauthorized, setupAuthInterceptors } from "@/services/authInterceptors";
import { clearStoredToken, getStoredToken, setStoredToken } from "@/utils/tokenStorage";

/**
 * A real axios instance wired with the real interceptors, but given a fake
 * `adapter` so no network call is ever made. The adapter resolves with
 * whatever { status, data } the test wants for a 2xx, and — matching what
 * axios's real http/xhr adapters do internally (this axios version's
 * `dispatchRequest` no longer centrally calls `settle()`; each adapter is
 * responsible for it) — rejects with a real AxiosError carrying `.response`
 * for a non-2xx, the same shape a genuine 401 response takes.
 */
function buildInstance(responses: Record<string, { status: number; data?: unknown }>) {
  const adapter: AxiosAdapter = async (config) => {
    const canned = responses[config.url ?? ""] ?? { status: 200, data: { ok: true } };
    const response = {
      data: canned.data ?? {},
      status: canned.status,
      statusText: "",
      headers: {},
      config,
    };
    if (canned.status >= 200 && canned.status < 300) {
      return response;
    }
    throw new AxiosError(
      `Request failed with status code ${canned.status}`,
      String(canned.status),
      config,
      undefined,
      response as never,
    );
  };
  const instance = axios.create({ adapter });
  setupAuthInterceptors(instance);
  return instance;
}

let unsubscribe: (() => void) | null = null;

beforeEach(() => {
  localStorage.clear();
});

afterEach(() => {
  unsubscribe?.();
  unsubscribe = null;
  localStorage.clear();
});

describe("request interceptor", () => {
  it("attaches Authorization: Bearer <token> when a token is stored", async () => {
    setStoredToken("abc.def.ghi");
    const instance = buildInstance({ "/api/students": { status: 200, data: { ok: true } } });

    const response = await instance.get("/api/students");

    expect(response.config.headers.get("Authorization")).toBe("Bearer abc.def.ghi");
  });

  it("sends no Authorization header when there is no stored token", async () => {
    clearStoredToken();
    const instance = buildInstance({ "/api/students": { status: 200, data: { ok: true } } });

    const response = await instance.get("/api/students");

    expect(response.config.headers.get("Authorization")).toBeUndefined();
  });
});

describe("response interceptor — 401 handling", () => {
  it("clears the token and fires the unauthorized handler on a 401 from an authenticated call", async () => {
    setStoredToken("abc.def.ghi");
    const handler = vi.fn();
    unsubscribe = onUnauthorized(handler);

    const instance = buildInstance({ "/api/students": { status: 401, data: {} } });

    await expect(instance.get("/api/students")).rejects.toMatchObject({
      response: { status: 401 },
    });

    expect(getStoredToken()).toBeNull();
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("does NOT fire the unauthorized handler for a 401 from /api/auth/login (pre-auth carve-out)", async () => {
    setStoredToken("abc.def.ghi");
    const handler = vi.fn();
    unsubscribe = onUnauthorized(handler);

    const instance = buildInstance({ "/api/auth/login": { status: 401, data: {} } });

    await expect(
      instance.post("/api/auth/login", { email: "x@example.com", password: "wrong" }),
    ).rejects.toMatchObject({ response: { status: 401 } });

    // A bad password is a form-level result, not a session expiry: the
    // stored token (if any) is left untouched and no global logout fires.
    expect(handler).not.toHaveBeenCalled();
    expect(getStoredToken()).toBe("abc.def.ghi");
  });

  it("does NOT fire the unauthorized handler for a 401 from /api/auth/password-reset/*", async () => {
    const handler = vi.fn();
    unsubscribe = onUnauthorized(handler);

    const instance = buildInstance({ "/api/auth/password-reset/verify": { status: 401, data: {} } });

    await expect(
      instance.post("/api/auth/password-reset/verify", { email: "x@example.com", otp: "000000" }),
    ).rejects.toMatchObject({ response: { status: 401 } });

    expect(handler).not.toHaveBeenCalled();
  });

  it("leaves a non-401 error response alone", async () => {
    setStoredToken("abc.def.ghi");
    const handler = vi.fn();
    unsubscribe = onUnauthorized(handler);

    const instance = buildInstance({ "/api/students": { status: 404, data: {} } });

    await expect(instance.get("/api/students")).rejects.toMatchObject({
      response: { status: 404 },
    });

    expect(handler).not.toHaveBeenCalled();
    expect(getStoredToken()).toBe("abc.def.ghi");
  });
});
