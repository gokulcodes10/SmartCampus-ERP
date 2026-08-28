package smartcampus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-registration payload. Always creates a {@code STUDENT} account —
 * PROJECT_PLAN.md clarification G1 restricts self-service registration to that role,
 * so this DTO deliberately carries no {@code role} field. Any {@code "role"} property
 * a caller sends in the JSON body is silently ignored by Jackson (unknown-property
 * failure is off by default) rather than accepted; {@code AuthService} hardcodes
 * {@link smartcampus.entity.Role#STUDENT} server-side regardless of what is sent.
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters.")
        String password,

        @NotBlank(message = "Full name is required.")
        @Size(max = 150, message = "Full name must be at most 150 characters.")
        String fullName) {
}
