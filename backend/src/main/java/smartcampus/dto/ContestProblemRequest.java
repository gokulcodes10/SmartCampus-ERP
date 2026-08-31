package smartcampus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /api/contests/{id}/problems} (ADMIN only). */
public record ContestProblemRequest(
        @NotNull Long problemId, @NotNull @Min(1) Integer ordinal, @NotNull @Min(1) Integer points) {}
