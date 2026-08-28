package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Course;

/**
 * Response representation of a {@code Course}, safe to return in list, detail, create,
 * and update endpoints.
 */
public record CourseResponse(
        Long id,
        String code,
        String name,
        Long departmentId,
        String departmentName,
        Integer durationSemesters,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Reads the lazy {@code department} association, so the caller must still be
     * inside the persistence context that loaded {@code course} (i.e. call this from
     * within the {@code @Transactional} service method, not after it returns) — the
     * same rule {@code StudentResponse}/{@code FacultyResponse} document for their own
     * denormalized name fields.
     */
    public static CourseResponse from(Course course) {
        var department = course.getDepartment();
        return new CourseResponse(
                course.getId(),
                course.getCode(),
                course.getName(),
                department != null ? department.getId() : null,
                department != null ? department.getName() : null,
                course.getDurationSemesters(),
                course.getCreatedAt(),
                course.getUpdatedAt());
    }
}
