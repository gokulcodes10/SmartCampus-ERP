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
 * Request body for {@code POST /api/contests} (ADMIN only).
 *
 * <p>{@code status} is declared here — not defaulted or omitted — because the admin UI
 * has a publish control (DRAFT → PUBLISHED); a request DTO without this field would
 * silently drop the toggle's value, the Phase 3 "button that does nothing" trap.
 *
 * <p>{@code endTime <= startTime} is rejected by the service with a {@code
 * BadRequestException} before it would otherwise hit {@code chk_coding_contests_window}
 * and come back as an opaque 409.
 */
public record ContestCreateRequest(
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
