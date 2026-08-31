package smartcampus.dto;

import smartcampus.entity.FacultySubjectAssignment;

/**
 * One (subject, academic year, semester, section) tuple a faculty member is assigned to
 * teach, enriched with subject/course display fields and the current active enrollment
 * count. This is how every faculty screen discovers which tuples it is authorized to
 * act on via {@link smartcampus.service.ScopedWriteAuthorizer}.
 */
public record TeachingClassResponse(
        Long assignmentId,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer subjectCredits,
        Long courseId,
        String courseCode,
        String courseName,
        String academicYear,
        Integer semester,
        String section,
        long enrolledStudentCount) {

    /**
     * Reads the lazy {@code subject} association (and, through it, {@code course}), so
     * the caller must still be inside the persistence context that loaded {@code
     * assignment} (i.e. call this from within the {@code @Transactional} service
     * method, not after it returns).
     */
    public static TeachingClassResponse from(
            FacultySubjectAssignment assignment, long enrolledStudentCount) {
        return new TeachingClassResponse(
                assignment.getId(),
                assignment.getSubject().getId(),
                assignment.getSubject().getCode(),
                assignment.getSubject().getName(),
                assignment.getSubject().getCredits(),
                assignment.getSubject().getCourse().getId(),
                assignment.getSubject().getCourse().getCode(),
                assignment.getSubject().getCourse().getName(),
                assignment.getAcademicYear(),
                assignment.getSemester(),
                assignment.getSection(),
                enrolledStudentCount);
    }
}
