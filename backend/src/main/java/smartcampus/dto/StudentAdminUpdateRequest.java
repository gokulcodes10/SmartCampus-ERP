package smartcampus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Admin edit of an already-assigned student's academic placement — department,
 * course, current semester, section, admission year.
 *
 * <p>Partial-update semantics: a field left {@code null} is left unchanged. There is
 * deliberately no {@code registerNumber} or {@code status} field here — those are
 * only ever set together, atomically, by {@code StudentService.activate} /
 * {@code deactivate} / {@code reactivate}, which is what keeps
 * {@code chk_students_active_requires_assignment} satisfiable: this endpoint can only
 * ever replace one non-null value with another, never null out a field a CHECK
 * constraint requires while the student is ACTIVE.
 */
public record StudentAdminUpdateRequest(
        Long departmentId,
        Long courseId,
        @Min(value = 1, message = "Current semester must be at least 1.") Integer currentSemester,
        @Size(max = 10, message = "Section must be at most 10 characters.") String section,
        @Min(value = 1900, message = "Admission year is not valid.") Integer admissionYear) {
}
