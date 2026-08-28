package smartcampus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new {@code Course} via {@code POST /api/courses}.
 * Restricted to ADMIN.
 */
public record CourseCreateRequest(
        @NotBlank(message = "Course code is required.")
        @Size(max = 20, message = "Course code must not exceed 20 characters.")
        String code,

        @NotBlank(message = "Course name is required.")
        @Size(max = 150, message = "Course name must not exceed 150 characters.")
        String name,

        @NotNull(message = "Department ID is required.")
        Long departmentId,

        @Min(value = 1, message = "Duration must be at least 1 semester.")
        Integer durationSemesters) {}
