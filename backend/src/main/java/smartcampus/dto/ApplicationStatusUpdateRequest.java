package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.ApplicationStatus;

/**
 * Request to update an application's status (§36).
 */
public record ApplicationStatusUpdateRequest(
    @NotNull ApplicationStatus status,
    @Size(max = 500) String decisionNote) {}
