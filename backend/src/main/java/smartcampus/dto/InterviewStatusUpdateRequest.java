package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.InterviewOutcome;
import smartcampus.entity.InterviewStatus;

/** PUT /api/interviews/{id}/status. Never changes {@code scheduledStart}/{@code scheduledEnd}. */
public record InterviewStatusUpdateRequest(
        @NotNull InterviewStatus status,
        InterviewOutcome outcome,
        String feedback,
        @Size(max = 500) String cancellationReason) {}
