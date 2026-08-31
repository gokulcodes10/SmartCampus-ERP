package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Request body shared by {@code POST /api/ai/study-plans/generate} and {@code POST
 * /api/ai/study-plans/revision-schedule}. {@code title} and {@code goal} are optional
 * hints to the model; {@code subjectIds} narrows generation to those subjects when
 * present.
 */
public record AIStudyPlanGenerateRequest(
        @Size(max = 150) String title,
        @Size(max = 500) String goal,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        List<Long> subjectIds,
        @Min(15) @Max(720) Integer dailyMinutes) {}
