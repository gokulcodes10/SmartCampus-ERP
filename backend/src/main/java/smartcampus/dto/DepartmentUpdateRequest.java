package smartcampus.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing {@code Department} via {@code PUT /api/departments/{id}}.
 * Restricted to ADMIN. All fields are optional.
 */
public record DepartmentUpdateRequest(
        @Size(max = 10, message = "Department code must not exceed 10 characters.")
        String code,

        @Size(max = 100, message = "Department name must not exceed 100 characters.")
        String name) {}
