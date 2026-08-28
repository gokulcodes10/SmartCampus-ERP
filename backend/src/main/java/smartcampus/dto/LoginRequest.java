package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;

/** Login payload. Field-level validation only checks presence — a blank email or
 * password is a {@code VALIDATION_FAILED} 400 from {@code GlobalExceptionHandler},
 * while a well-formed but wrong email/password pair is a generic, non-enumerating
 * {@code InvalidCredentialsException} from {@code AuthService}. */
public record LoginRequest(

        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Password is required.")
        String password) {
}
