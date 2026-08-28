package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/faculty-subject-assignments} — an admin assigning a
 * faculty member to teach a subject/section in a given academic year and semester
 * (PROJECT_PLAN.md clarification G2). ADMIN-only.
 *
 * <p>This exact tuple is what {@code smartcampus.service.AcademicAccessGuard} checks
 * against for every faculty authorization decision in this and later phases, so every
 * field here is significant: an assignment to one section does not extend to another.
 */
public record FacultySubjectAssignmentRequest(
        @NotNull(message = "Faculty ID is required.")
        Long facultyId,

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
