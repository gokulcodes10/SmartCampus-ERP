package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.ContestPhase;
import smartcampus.entity.ContestStatus;

/**
 * Row shape for {@code GET /api/contests} (§44 paginated list).
 *
 * <p>{@link #status} is the stored AUTHORING lifecycle (DRAFT/PUBLISHED/CANCELLED);
 * {@link #phase} is the time-derived UPCOMING/RUNNING/ENDED value, computed fresh on
 * every read from {@code startTime}/{@code endTime} against {@code now} — see {@link
 * ContestPhase}. Both are exposed because the UI shows them separately.
 *
 * <p>{@code registered} is {@code true} only when the caller is a STUDENT registered
 * for this contest; it is always {@code false} for ADMIN/FACULTY callers.
 */
public record ContestSummaryResponse(
        Long id,
        String slug,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        ContestStatus status,
        ContestPhase phase,
        Integer penaltyMinutesPerWrongAttempt,
        int problemCount,
        long participantCount,
        boolean registered,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
