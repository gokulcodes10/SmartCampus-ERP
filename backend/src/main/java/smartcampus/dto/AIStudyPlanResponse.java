package smartcampus.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.AIStudyPlan;
import smartcampus.entity.AIStudyPlanItem;
import smartcampus.entity.AIStudyPlanSource;
import smartcampus.entity.AIStudyPlanType;
import smartcampus.entity.AIStudyPlanStatus;

/** Response representation of an {@link AIStudyPlan} with its full item list. */
public record AIStudyPlanResponse(
        Long id,
        Long conversationId,
        AIStudyPlanType planType,
        String title,
        String goal,
        LocalDate startDate,
        LocalDate endDate,
        AIStudyPlanStatus status,
        AIStudyPlanSource source,
        String model,
        boolean edited,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AIStudyPlanItemResponse> items) {

    /**
     * Reads the lazy {@code conversation} association (for its id only) and, through
     * {@code items}, each item's lazy {@code subject} association — so the caller must
     * still be inside the persistence context that loaded {@code p} and {@code items}.
     */
    public static AIStudyPlanResponse from(AIStudyPlan p, List<AIStudyPlanItem> items) {
        return new AIStudyPlanResponse(
                p.getId(),
                p.getConversation() != null ? p.getConversation().getId() : null,
                p.getPlanType(),
                p.getTitle(),
                p.getGoal(),
                p.getStartDate(),
                p.getEndDate(),
                p.getStatus(),
                p.getSource(),
                p.getModel(),
                p.isEdited(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                items.stream().map(AIStudyPlanItemResponse::from).toList());
    }
}
