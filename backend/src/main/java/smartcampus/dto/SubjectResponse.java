package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.Subject;

/**
 * Response representation of a {@code Subject}, safe to return in list, detail, create,
 * and update endpoints.
 */
public record SubjectResponse(
        Long id,
        String code,
        String name,
        Integer credits,
        Integer semester,
        Long courseId,
        String courseCode,
        String courseName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Reads the lazy {@code course} association, so the caller must still be inside
     * the persistence context that loaded {@code subject} (i.e. call this from within
     * the {@code @Transactional} service method, not after it returns) — the same rule
     * {@code StudentResponse}/{@code FacultyResponse} document for their own
     * denormalized name fields.
     */
    public static SubjectResponse from(Subject subject) {
        var course = subject.getCourse();
        return new SubjectResponse(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                subject.getCredits(),
                subject.getSemester(),
                course != null ? course.getId() : null,
                course != null ? course.getCode() : null,
                course != null ? course.getName() : null,
                subject.getCreatedAt(),
                subject.getUpdatedAt());
    }
}
