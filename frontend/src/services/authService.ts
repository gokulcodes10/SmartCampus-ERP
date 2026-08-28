import api from "@/services/api";
import type {
  AuthenticatedUser,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  ResetPasswordRequest,
  ResetPasswordResponse,
  VerifyOtpRequest,
  VerifyOtpResponse,
} from "@/types/auth";

/**
 * Thin wrappers around the real `/api/auth/*` endpoints — one function per
 * backend call, typed request/response, no mock data (scope §69).
 *
 * Endpoint contract — reconciled against the real `AuthController` /
 * `PasswordResetController` built by the backend auth agents:
 *
 *   POST /api/auth/register                  { email, password, fullName } -> 201 AuthenticatedUser (role forced to STUDENT, G1)
 *   POST /api/auth/login                     { email, password }           -> 200 { token, user: AuthenticatedUser }
 *   GET  /api/auth/me                        (Bearer token)                -> 200 AuthenticatedUser
 *   POST /api/auth/password-reset/request    { email }                     -> 200 { message } — always 200, non-enumerating
 *   POST /api/auth/password-reset/verify     { email, otp }                -> 200 { message } — read-only check, does not consume the OTP
 *   POST /api/auth/password-reset/reset      { email, otp, newPassword }   -> 200 { message } — validates the OTP again and consumes it
 *
 * All error responses are the §47 envelope; see utils/apiError.ts.
 */

export async function login(payload: LoginRequest): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>("/api/auth/login", payload);
  return data;
}

export async function register(payload: RegisterRequest): Promise<AuthenticatedUser> {
  const { data } = await api.post<AuthenticatedUser>("/api/auth/register", payload);
  return data;
}

export async function fetchCurrentUser(): Promise<AuthenticatedUser> {
  const { data } = await api.get<AuthenticatedUser>("/api/auth/me");
  return data;
}

export async function forgotPassword(
  payload: ForgotPasswordRequest,
): Promise<ForgotPasswordResponse> {
  const { data } = await api.post<ForgotPasswordResponse>(
    "/api/auth/password-reset/request",
    payload,
  );
  return data;
}

export async function verifyOtp(payload: VerifyOtpRequest): Promise<VerifyOtpResponse> {
  const { data } = await api.post<VerifyOtpResponse>(
    "/api/auth/password-reset/verify",
    payload,
  );
  return data;
}

export async function resetPassword(
  payload: ResetPasswordRequest,
): Promise<ResetPasswordResponse> {
  const { data } = await api.post<ResetPasswordResponse>(
    "/api/auth/password-reset/reset",
    payload,
  );
  return data;
}
