package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Faculty;
import smartcampus.entity.FacultyStatus;

/**
 * The safe, public shape of a {@link Faculty} profile — the linked {@link
 * smartcampus.entity.User}'s email and name are flattened in, but never its password
 * or hash.
 */
public record FacultyResponse(
        Long id,
        Long userId,
        String email,
        String fullName,
        String employeeCode,
        Long departmentId,
        String departmentName,
        String designation,
        FacultyStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Builds the response from a fully-initialized {@link Faculty}. Must be called
     * while the owning persistence context is still open — {@code user} and {@code
     * department} are both {@code LAZY} associations.
     */
    public static FacultyResponse from(Faculty faculty) {
        var user = faculty.getUser();
        var department = faculty.getDepartment();
        return new FacultyResponse(
                faculty.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                faculty.getEmployeeCode(),
                department.getId(),
                department.getName(),
                faculty.getDesignation(),
                faculty.getStatus(),
                faculty.getCreatedAt(),
                faculty.getUpdatedAt());
    }
}
