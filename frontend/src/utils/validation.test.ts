import { describe, expect, it } from "vitest";

import { MIN_PASSWORD_LENGTH, OTP_LENGTH, isValidEmail, isValidOtp, isValidPassword } from "@/utils/validation";

describe("isValidEmail", () => {
  it("accepts a well-formed email", () => {
    expect(isValidEmail("student@example.com")).toBe(true);
  });

  it("tolerates surrounding whitespace", () => {
    expect(isValidEmail("  student@example.com  ")).toBe(true);
  });

  it.each(["", "no-at-sign", "missing-domain@", "@missing-local.com", "spaces in@email.com"])(
    "rejects %j",
    (value) => {
      expect(isValidEmail(value)).toBe(false);
    },
  );
});

describe("isValidPassword", () => {
  it(`requires at least ${MIN_PASSWORD_LENGTH} characters`, () => {
    expect(isValidPassword("a".repeat(MIN_PASSWORD_LENGTH - 1))).toBe(false);
    expect(isValidPassword("a".repeat(MIN_PASSWORD_LENGTH))).toBe(true);
  });

  it("rejects an empty password", () => {
    expect(isValidPassword("")).toBe(false);
  });
});

describe("isValidOtp", () => {
  it(`accepts exactly ${OTP_LENGTH} digits`, () => {
    expect(isValidOtp("1".repeat(OTP_LENGTH))).toBe(true);
  });

  it("rejects too few or too many digits", () => {
    expect(isValidOtp("1".repeat(OTP_LENGTH - 1))).toBe(false);
    expect(isValidOtp("1".repeat(OTP_LENGTH + 1))).toBe(false);
  });

  it("rejects non-digit characters", () => {
    expect(isValidOtp("12345a")).toBe(false);
  });

  it("tolerates surrounding whitespace", () => {
    expect(isValidOtp(`  ${"1".repeat(OTP_LENGTH)}  `)).toBe(true);
  });
});
