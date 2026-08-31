package smartcampus.dto;

import java.time.LocalDateTime;

/**
 * A student's registration and current standing in one contest — the response of
 * {@code POST /api/contests/{id}/register} and {@code GET /api/contests/{id}/me}.
 *
 * <p>{@code totalScore}/{@code problemsSolved}/{@code penaltySeconds}/{@code
 * lastAcceptedAt} are the denormalized values written wholesale by {@code
 * ContestScoringService} — never incremented in place — so they can always be
 * regenerated from {@code coding_submissions} alone.
 */
public record ContestParticipantResponse(
        Long id,
        Long contestId,
        Long studentId,
        String studentName,
        String registerNumber,
        LocalDateTime registeredAt,
        Integer totalScore,
        Integer problemsSolved,
        Integer penaltySeconds,
        LocalDateTime lastAcceptedAt) {}
