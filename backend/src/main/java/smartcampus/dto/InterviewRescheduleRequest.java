package smartcampus.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * PUT /api/interviews/{id}/reschedule. Sets status = RESCHEDULED and re-runs the
 * conflict check, excluding this interview from its own overlap test.
 */
public record InterviewRescheduleRequest(
        @NotNull LocalDateTime scheduledStart, @NotNull LocalDateTime scheduledEnd) {}
