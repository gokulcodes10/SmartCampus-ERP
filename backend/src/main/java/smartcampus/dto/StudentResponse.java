package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;

/**
 * The safe, public shape of a {@link Student} profile — the linked {@link
 * smartcampus.entity.User}'s email and name are flattened in, but never its password
 * or hash. {@code departmentId}/{@code courseId} (and their names) are {@code null}
 * while the profile is {@code PENDING}; the G1 activation flow is what fills them in.
 */
public record StudentResponse(
        Long id,
        Long userId,
        String email,
        String fullName,
        String registerNumber,
        Long departmentId,
        String departmentName,
        Long courseId,
        String courseName,
        Integer currentSemester,
        String section,
        Integer admissionYear,
        StudentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Builds the response from a fully-initialized {@link Student}. Must be called
     * while the owning persistence context is still open — {@code user}, {@code
     * department} and {@code course} are all {@code LAZY} associations.
     */
    public static StudentResponse from(Student student) {
        var user = student.getUser();
        var department = student.getDepartment();
        var course = student.getCourse();
        return new StudentResponse(
                student.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                student.getRegisterNumber(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                course != null ? course.getId() : null,
                course != null ? course.getName() : null,
                student.getCurrentSemester(),
                student.getSection(),
                student.getAdmissionYear(),
                student.getStatus(),
                student.getCreatedAt(),
                student.getUpdatedAt());
    }
}
