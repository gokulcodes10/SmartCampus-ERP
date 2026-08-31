package smartcampus.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import smartcampus.entity.AIStudyPlanSource;
import smartcampus.entity.AIStudyPlanStatus;
import smartcampus.entity.AIStudyPlanType;

/**
 * A lighter {@link AIStudyPlan} representation for the plan list screen — item counts
 * instead of the full item list. Assembled by the service.
 */
public record AIStudyPlanSummaryResponse(
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
        long itemCount,
        long completedItemCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
