package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import smartcampus.entity.AIStudyPlanStatus;

/** Request body for {@code PUT /api/ai/study-plans/{id}}: full replace of the editable plan fields. */
public record AIStudyPlanUpdateRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 500) String goal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull AIStudyPlanStatus status) {}
