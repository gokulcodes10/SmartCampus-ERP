package smartcampus.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.Role;

/**
 * Admin-only account provisioning payload.
 *
 * <p>Unlike {@link RegisterRequest} this one <em>does</em> carry a {@code role}, and
 * that is deliberate: PROJECT_PLAN.md clarification G1 restricts self-registration to
 * {@code STUDENT} precisely so that {@code FACULTY} and {@code ADMIN} accounts are
 * created only by an existing admin, through this DTO, behind an {@code ADMIN}-only
 * route. The role is honoured here because reaching the endpoint at all already
 * required an admin JWT.
 */
public record ProvisionUserRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters.")
        String password,

        @NotBlank(message = "Full name is required.")
        @Size(max = 150, message = "Full name must be at most 150 characters.")
        String fullName,

        @NotNull(message = "Role is required.")
        Role role) {
}
