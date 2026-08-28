package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Enrollment;
import smartcampus.entity.EnrollmentStatus;

/**
 * Response representation of an {@code Enrollment}, safe to return from create, detail,
 * list and status-update endpoints. Carries a few denormalized display fields (student
 * name/register number, subject code/name) so a caller does not need a second round
 * trip to render an enrollment row.
 */
public record EnrollmentResponse(
        Long id,
        Long studentId,
        String studentRegisterNumber,
        String studentName,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        EnrollmentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Reads the lazy {@code student} and {@code subject} associations, so the caller
     * must still be inside the persistence context that loaded {@code enrollment}
     * (i.e. call this from within the {@code @Transactional} service method, not after
     * it returns).
     */
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().getRegisterNumber(),
                enrollment.getStudent().getUser().getFullName(),
                enrollment.getSubject().getId(),
                enrollment.getSubject().getCode(),
                enrollment.getSubject().getName(),
                enrollment.getAcademicYear(),
                enrollment.getSemester(),
                enrollment.getSection(),
                enrollment.getStatus(),
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt());
    }
}
