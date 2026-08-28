package smartcampus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing {@code Course} via {@code PUT /api/courses/{id}}.
 * Restricted to ADMIN. All fields are optional.
 */
public record CourseUpdateRequest(
        @Size(max = 20, message = "Course code must not exceed 20 characters.")
        String code,

        @Size(max = 150, message = "Course name must not exceed 150 characters.")
        String name,

        Long departmentId,

        @Min(value = 1, message = "Duration must be at least 1 semester.")
        Integer durationSemesters) {}
