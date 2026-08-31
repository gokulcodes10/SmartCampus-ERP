package smartcampus.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import smartcampus.entity.AIStudyPlanItem;
import smartcampus.entity.Subject;

/** Response representation of one {@link AIStudyPlanItem}. */
public record AIStudyPlanItemResponse(
        Long id,
        Long subjectId,
        String subjectCode,
        String subjectName,
        String subjectLabel,
        int position,
        LocalDate scheduledDate,
        String title,
        String description,
        Integer durationMinutes,
        boolean completed,
        LocalDateTime completedAt) {

    /**
     * Reads the lazy {@code subject} association, so the caller must still be inside
     * the persistence context that loaded {@code i} (i.e. call this from within the
     * {@code @Transactional} service method, not after it returns). All three
     * subject-derived fields are null when {@code i.getSubject()} is null.
     */
    public static AIStudyPlanItemResponse from(AIStudyPlanItem i) {
        Subject subject = i.getSubject();
        return new AIStudyPlanItemResponse(
                i.getId(),
                subject != null ? subject.getId() : null,
                subject != null ? subject.getCode() : null,
                subject != null ? subject.getName() : null,
                i.getSubjectLabel(),
                i.getPosition(),
                i.getScheduledDate(),
                i.getTitle(),
                i.getDescription(),
                i.getDurationMinutes(),
                i.isCompleted(),
                i.getCompletedAt());
    }
}
