/**
 * Types for the Phase 2 authentication API. These match `smartcampus.entity.Role`
 * and the `/api/auth/*` endpoints documented in `frontend/src/services/authService.ts`.
 */

export type Role = "STUDENT" | "FACULTY" | "ADMIN";

/** Shape returned by `GET /api/auth/me` and by `POST /api/auth/register` — never includes a password. */
export interface AuthenticatedUser {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
  updatedAt?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: AuthenticatedUser;
}

/** Self-registration is STUDENT-only (scope clarification G1) — there is no `role` field. */
export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ForgotPasswordResponse {
  message: string;
}

export interface VerifyOtpRequest {
  email: string;
  otp: string;
}

/**
 * The backend's verify step is a stateless, read-only check — it does not consume the
 * OTP or issue a separate reset credential, just confirms the code is currently valid.
 * The email/otp pair is carried forward by the caller (see VerifyOtpPage) and resent
 * to `resetPassword`, which validates it again server-side before consuming it.
 */
export interface VerifyOtpResponse {
  message: string;
}

export interface ResetPasswordRequest {
  email: string;
  otp: string;
  newPassword: string;
}

export interface ResetPasswordResponse {
  message: string;
}

/** The §47 API error envelope produced by `GlobalExceptionHandler`. */
export interface ErrorEnvelope {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
