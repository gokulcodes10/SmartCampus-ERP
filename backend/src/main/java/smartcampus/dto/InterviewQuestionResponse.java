package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestion;
import smartcampus.entity.InterviewQuestionCategory;
import smartcampus.entity.InterviewQuestionProgress;
import smartcampus.entity.InterviewQuestionSource;

/**
 * Response representation of an {@code InterviewQuestion}, merged with the caller's own
 * {@code InterviewQuestionProgress} row (if any) so the question bank list/detail screens
 * never need a second per-row lookup.
 */
public record InterviewQuestionResponse(
        Long id,
        InterviewQuestionCategory category,
        InterviewDifficulty difficulty,
        String question,
        String answer,
        String explanation,
        String companyName,
        String tags,
        InterviewQuestionSource source,
        String model,
        Long ownerStudentId,
        boolean mine,
        boolean completed,
        boolean bookmarked,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * {@code progress} may be {@code null} (the caller has no progress row for this
     * question, or the caller is not a student) — in that case {@code completed} and
     * {@code bookmarked} are {@code false} and {@code completedAt} is {@code null}.
     * {@code mine} is {@code true} iff {@code q.getOwnerStudent()} is non-null.
     *
     * <p>Reads the LAZY {@code ownerStudent} association, so this MUST be called from
     * inside the {@code @Transactional} service method that loaded {@code q} — {@code
     * spring.jpa.open-in-view} is {@code false} in this project, so touching a LAZY
     * association after the transaction has closed throws at serialization time.
     */
    public static InterviewQuestionResponse from(InterviewQuestion q, InterviewQuestionProgress progress) {
        boolean mine = q.getOwnerStudent() != null;
        boolean completed = progress != null && progress.isCompleted();
        boolean bookmarked = progress != null && progress.isBookmarked();
        LocalDateTime completedAt = progress != null ? progress.getCompletedAt() : null;
        return new InterviewQuestionResponse(
                q.getId(),
                q.getCategory(),
                q.getDifficulty(),
                q.getQuestion(),
                q.getAnswer(),
                q.getExplanation(),
                q.getCompanyName(),
                q.getTags(),
                q.getSource(),
                q.getModel(),
                mine ? q.getOwnerStudent().getId() : null,
                mine,
                completed,
                bookmarked,
                completedAt,
                q.getCreatedAt(),
                q.getUpdatedAt());
    }
}
