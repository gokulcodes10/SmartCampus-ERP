package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new {@code Department} via {@code POST /api/departments}.
 * Restricted to ADMIN.
 */
public record DepartmentCreateRequest(
        @NotBlank(message = "Department code is required.")
        @Size(max = 10, message = "Department code must not exceed 10 characters.")
        String code,

        @NotBlank(message = "Department name is required.")
        @Size(max = 100, message = "Department name must not exceed 100 characters.")
        String name) {}
