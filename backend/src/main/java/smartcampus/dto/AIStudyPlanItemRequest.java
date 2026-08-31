package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body shared by {@code POST /api/ai/study-plans/{id}/items} (add) and {@code
 * PUT /api/ai/study-plans/{id}/items/{itemId}} (update).
 *
 * <p>{@code completed} and {@code position} are OPTIONAL: {@code null} means "leave
 * unchanged" on update / "default" on create. Both fields exist here even though they
 * are optional because a field a request DTO does not declare is silently dropped by
 * Jackson (the Phase 3 "button that does nothing" trap) — the completion toggle and any
 * future reorder UI need them present.
 */
public record AIStudyPlanItemRequest(
        Long subjectId,
        @Size(max = 150) String subjectLabel,
        @NotNull LocalDate scheduledDate,
        @NotBlank @Size(max = 200) String title,
        String description,
        @Min(1) @Max(1440) Integer durationMinutes,
        Boolean completed,
        @Min(0) Integer position) {}
