import { AxiosError } from "axios";
import { describe, expect, it } from "vitest";

import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";
import type { ErrorEnvelope } from "@/types/auth";

/** Builds a real AxiosError carrying the scope-47 envelope as its response body. */
function envelopeError(envelope: Partial<ErrorEnvelope>, status = 400): AxiosError<ErrorEnvelope> {
  const full: ErrorEnvelope = {
    timestamp: "2026-08-28T10:15:30Z",
    status,
    error: "BAD_REQUEST",
    message: "",
    path: "/api/students",
    ...envelope,
  };
  return new AxiosError(
    "Request failed",
    "ERR_BAD_REQUEST",
    undefined,
    undefined,
    {
      data: full,
      status,
      statusText: "Bad Request",
      headers: {},
      config: {} as never,
    } as never,
  );
}

describe("extractErrorMessage", () => {
  it("pulls the §47 envelope message out of an AxiosError", () => {
    const err = envelopeError({ message: "Student not found" }, 404);
    expect(extractErrorMessage(err)).toBe("Student not found");
  });

  it("falls back to a network-specific message when there is no response", () => {
    const err = new AxiosError("Network Error", "ERR_NETWORK");
    expect(extractErrorMessage(err)).toBe("Cannot reach the server. Check your connection and try again.");
  });

  it("falls back to the provided default for a non-envelope error", () => {
    expect(extractErrorMessage(new Error("boom"), "custom fallback")).toBe("custom fallback");
  });

  it("uses the generic default when none is supplied", () => {
    expect(extractErrorMessage(new Error("boom"))).toBe("Something went wrong. Please try again.");
  });
});

describe("extractRawErrorMessage", () => {
  it("returns the raw §47 message with no fallback substitution", () => {
    const err = envelopeError({ message: "email: must be a valid email" });
    expect(extractRawErrorMessage(err)).toBe("email: must be a valid email");
  });

  it("returns null for a non-envelope error", () => {
    expect(extractRawErrorMessage(new Error("boom"))).toBeNull();
  });
});

describe("parseFieldErrors", () => {
  it("splits a joined validation message into known fields", () => {
    const { fieldErrors, formError } = parseFieldErrors(
      "email: must be a valid email; password: must be at least 8 characters",
      ["email", "password"],
    );
    expect(fieldErrors).toEqual({
      email: "must be a valid email",
      password: "must be at least 8 characters",
    });
    expect(formError).toBeNull();
  });

  it("routes anything not in knownFields to formError instead of dropping it", () => {
    const { fieldErrors, formError } = parseFieldErrors(
      "email: must be a valid email; unexpectedField: some reason",
      ["email"],
    );
    expect(fieldErrors).toEqual({ email: "must be a valid email" });
    expect(formError).toBe("unexpectedField: some reason");
  });

  it("treats a message with no colon as leftover, not a field", () => {
    const { fieldErrors, formError } = parseFieldErrors("something went wrong entirely", ["email"]);
    expect(fieldErrors).toEqual({});
    expect(formError).toBe("something went wrong entirely");
  });
});
