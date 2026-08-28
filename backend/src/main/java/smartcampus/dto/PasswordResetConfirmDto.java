package smartcampus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/auth/password-reset/reset}. */
public record PasswordResetConfirmDto(
        @NotBlank(message = "email is required") @Email(message = "email must be a valid address")
                String email,
        @NotBlank(message = "otp is required") String otp,
        @NotBlank(message = "newPassword is required")
                @Size(min = 8, max = 72, message = "newPassword must be 8-72 characters")
                String newPassword) {}
