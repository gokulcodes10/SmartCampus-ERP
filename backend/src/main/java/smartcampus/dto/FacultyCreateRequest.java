package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin creation of a {@link smartcampus.entity.Faculty} profile for an
 * already-provisioned {@code FACULTY} user (PROJECT_PLAN.md clarification G1: staff
 * accounts are admin-provisioned via {@code POST /api/users} first — this endpoint
 * attaches the academic profile to that user, it does not create the login account).
 */
public record FacultyCreateRequest(
        @NotNull(message = "User is required.") Long userId,

        @NotBlank(message = "Employee code is required.")
        @Size(max = 20, message = "Employee code must be at most 20 characters.")
        String employeeCode,

        @NotNull(message = "Department is required.") Long departmentId,

        @Size(max = 100, message = "Designation must be at most 100 characters.")
        String designation) {
}
