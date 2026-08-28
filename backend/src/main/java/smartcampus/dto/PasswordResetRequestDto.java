package smartcampus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/auth/password-reset/request}. */
public record PasswordResetRequestDto(
        @NotBlank(message = "email is required") @Email(message = "email must be a valid address")
                String email) {}
