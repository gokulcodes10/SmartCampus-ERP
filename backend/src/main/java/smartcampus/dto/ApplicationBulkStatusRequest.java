package smartcampus.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import smartcampus.entity.ApplicationStatus;

/**
 * Request to update multiple applications' status in bulk (§36).
 */
public record ApplicationBulkStatusRequest(
    @NotEmpty List<Long> applicationIds,
    @NotNull ApplicationStatus status,
    @Size(max = 500) String decisionNote) {}
