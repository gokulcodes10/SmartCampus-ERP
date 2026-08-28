/**
 * Small, pure client-side validation helpers for the auth forms. These are a
 * first line of defense for UX only — the backend re-validates everything
 * and is the source of truth for what's actually accepted.
 */

export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

export const MIN_PASSWORD_LENGTH = 8;

export function isValidPassword(value: string): boolean {
  return value.length >= MIN_PASSWORD_LENGTH;
}

export const OTP_LENGTH = 6;

export function isValidOtp(value: string): boolean {
  return new RegExp(`^\\d{${OTP_LENGTH}}$`).test(value.trim());
}
