package smartcampus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The G1 admin activation payload: the four fields {@code
 * chk_students_active_requires_assignment} (V3__academic.sql) requires before a
 * student can move from {@code PENDING} to {@code ACTIVE} — register number,
 * department, course, and current semester. {@code section} and {@code
 * admissionYear} are not part of that CHECK constraint but are accepted here too
 * since activation is the natural point at which an admin sets them.
 */
public record StudentActivateRequest(
        @NotBlank(message = "Register number is required.")
        @Size(max = 20, message = "Register number must be at most 20 characters.")
        String registerNumber,

        @NotNull(message = "Department is required.") Long departmentId,

        @NotNull(message = "Course is required.") Long courseId,

        @NotNull(message = "Current semester is required.")
        @Min(value = 1, message = "Current semester must be at least 1.")
        Integer currentSemester,

        @Size(max = 10, message = "Section must be at most 10 characters.") String section,

        @Min(value = 1900, message = "Admission year is not valid.") Integer admissionYear) {
}
