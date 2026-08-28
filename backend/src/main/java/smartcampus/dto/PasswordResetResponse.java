package smartcampus.dto;

/**
 * Uniform response body for every {@code /api/auth/password-reset/**} endpoint.
 *
 * <p>Deliberately just a human-readable message and nothing else - in particular
 * never a boolean "account exists" flag or the token/OTP itself - so the same shape
 * works for the non-enumerating success and failure responses described in
 * {@code smartcampus.service.PasswordResetService}.
 */
public record PasswordResetResponse(String message) {}
