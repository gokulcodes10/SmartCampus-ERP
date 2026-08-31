package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import smartcampus.entity.JobStatus;

/**
 * Request to update a job's status (§35).
 */
public record JobStatusUpdateRequest(
    @NotNull JobStatus status) {}
