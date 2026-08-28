package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.FacultySubjectAssignment;

/**
 * Response representation of a {@code FacultySubjectAssignment}, safe to return from
 * create, detail and list endpoints. Carries a few denormalized display fields (faculty
 * name/employee code, subject code/name) so a caller does not need a second round trip.
 */
public record FacultySubjectAssignmentResponse(
        Long id,
        Long facultyId,
        String facultyEmployeeCode,
        String facultyName,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Reads the lazy {@code faculty} and {@code subject} associations, so the caller
     * must still be inside the persistence context that loaded {@code assignment}.
     */
    public static FacultySubjectAssignmentResponse from(FacultySubjectAssignment assignment) {
        return new FacultySubjectAssignmentResponse(
                assignment.getId(),
                assignment.getFaculty().getId(),
                assignment.getFaculty().getEmployeeCode(),
                assignment.getFaculty().getUser().getFullName(),
                assignment.getSubject().getId(),
                assignment.getSubject().getCode(),
                assignment.getSubject().getName(),
                assignment.getAcademicYear(),
                assignment.getSemester(),
                assignment.getSection(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt());
    }
}
