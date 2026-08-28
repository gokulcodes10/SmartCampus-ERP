package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing {@code Subject} via {@code PUT /api/subjects/{id}}.
 * Restricted to ADMIN. All fields are optional.
 */
public record SubjectUpdateRequest(
        @Size(max = 20, message = "Subject code must not exceed 20 characters.")
        String code,

        @Size(max = 150, message = "Subject name must not exceed 150 characters.")
        String name,

        @Min(value = 1, message = "Credits must be at least 1.")
        @Max(value = 10, message = "Credits must not exceed 10.")
        Integer credits,

        @Min(value = 1, message = "Semester must be at least 1.")
        Integer semester,

        Long courseId) {}
