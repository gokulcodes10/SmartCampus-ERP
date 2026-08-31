package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import smartcampus.entity.ContestStatus;

/**
 * Request body for {@code PUT /api/contests/{id}} (ADMIN only). Component list
 * identical to {@link ContestCreateRequest} — see its javadoc for why {@code status}
 * must be present.
 */
public record ContestUpdateRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(
                regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "Slug must be lowercase alphanumeric words separated by hyphens.")
        String slug,
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull ContestStatus status,
        @NotNull @Min(0) @Max(1440) Integer penaltyMinutesPerWrongAttempt) {}
