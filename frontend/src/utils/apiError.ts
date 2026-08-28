import { AxiosError } from "axios";

import type { ErrorEnvelope } from "@/types/auth";

/**
 * Pulls a human-readable message out of a failed axios call against the
 * backend's §47 error envelope (`{ timestamp, status, error, message, path }`),
 * produced by `GlobalExceptionHandler`. Falls back to a generic message for
 * anything that isn't that shape (network failure, the server being down).
 */
export function extractErrorMessage(
  error: unknown,
  fallback = "Something went wrong. Please try again.",
): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ErrorEnvelope | undefined;
    if (data?.message) {
      return data.message;
    }
    if (!error.response) {
      return "Cannot reach the server. Check your connection and try again.";
    }
  }
  return fallback;
}

/**
 * `GlobalExceptionHandler.handleValidation` joins every failed `@Valid` field into one
 * `message` string, `"field: reason; otherField: reason"` (see
 * backend/src/main/java/smartcampus/exception/GlobalExceptionHandler.java). Admin forms
 * want to show each reason next to its own field rather than as one undifferentiated
 * banner, so this splits that string back apart. `knownFields` limits matches to the
 * form's actual field names (backend DTO field names may not match the frontend's) —
 * anything left over is returned as `formError` for the banner instead of being dropped.
 */
export function parseFieldErrors(
  message: string,
  knownFields: readonly string[],
): { fieldErrors: Record<string, string>; formError: string | null } {
  const fieldErrors: Record<string, string> = {};
  const leftover: string[] = [];

  for (const part of message.split(";").map((s) => s.trim()).filter(Boolean)) {
    const colonIndex = part.indexOf(":");
    const field = colonIndex === -1 ? "" : part.slice(0, colonIndex).trim();
    if (colonIndex !== -1 && knownFields.includes(field)) {
      fieldErrors[field] = part.slice(colonIndex + 1).trim();
    } else {
      leftover.push(part);
    }
  }

  return {
    fieldErrors,
    formError: leftover.length > 0 ? leftover.join("; ") : null,
  };
}

/**
 * Extracts the raw §47 `message` (unlike {@link extractErrorMessage}, does not fall
 * back to a generic string) so a caller can run it through {@link parseFieldErrors}.
 * Returns null for anything that isn't a §47 envelope (network failure, unexpected shape).
 */
export function extractRawErrorMessage(error: unknown): string | null {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ErrorEnvelope | undefined;
    if (data?.message) {
      return data.message;
    }
  }
  return null;
}
