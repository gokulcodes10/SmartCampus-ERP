package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new {@code Subject} via {@code POST /api/subjects}.
 * Restricted to ADMIN.
 */
public record SubjectCreateRequest(
        @NotBlank(message = "Subject code is required.")
        @Size(max = 20, message = "Subject code must not exceed 20 characters.")
        String code,

        @NotBlank(message = "Subject name is required.")
        @Size(max = 150, message = "Subject name must not exceed 150 characters.")
        String name,

        @NotNull(message = "Credits are required.")
        @Min(value = 1, message = "Credits must be at least 1.")
        @Max(value = 10, message = "Credits must not exceed 10.")
        Integer credits,

        @NotNull(message = "Semester is required.")
        @Min(value = 1, message = "Semester must be at least 1.")
        Integer semester,

        @NotNull(message = "Course ID is required.")
        Long courseId) {}
