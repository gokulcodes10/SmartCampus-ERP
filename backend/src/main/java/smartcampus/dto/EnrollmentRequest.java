package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/enrollments} — an admin enrolling a student in a
 * subject for a given academic year, semester and section. ADMIN-only.
 *
 * <p>{@code academicYear} follows the {@code "2025-2026"} shape used throughout the
 * schema ({@code enrollments.academic_year VARCHAR(9)}).
 */
public record EnrollmentRequest(
        @NotNull(message = "Student ID is required.")
        Long studentId,

        @NotNull(message = "Subject ID is required.")
        Long subjectId,

        @NotBlank(message = "Academic year is required.")
        @Pattern(regexp = "\\d{4}-\\d{4}", message = "Academic year must be in the form \"2025-2026\".")
        String academicYear,

        @NotNull(message = "Semester is required.")
        @Positive(message = "Semester must be a positive number.")
        Integer semester,

        @NotBlank(message = "Section is required.")
        @Size(max = 10, message = "Section must not exceed 10 characters.")
        String section) {}
