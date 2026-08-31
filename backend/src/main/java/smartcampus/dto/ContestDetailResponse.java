package smartcampus.dto;

import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.ContestPhase;
import smartcampus.entity.ContestStatus;

/**
 * {@code GET /api/contests/{id}} — every {@link ContestSummaryResponse} component plus
 * the full description, authorship, and the contest's problem set.
 *
 * <p>{@code problemsVisible}/{@code problems}: for a non-ADMIN caller while the
 * contest's derived {@link ContestPhase} is {@code UPCOMING}, {@code problems} is an
 * empty list and {@code problemsVisible} is {@code false} — a contest's problem set is
 * hidden until it starts. ADMIN always sees the full list.
 */
public record ContestDetailResponse(
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
        LocalDateTime updatedAt,
        String description,
        Long createdById,
        String createdByName,
        boolean problemsVisible,
        List<ContestProblemResponse> problems) {}
