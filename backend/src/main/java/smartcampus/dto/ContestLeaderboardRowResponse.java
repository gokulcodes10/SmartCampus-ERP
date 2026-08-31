package smartcampus.dto;

import java.time.LocalDateTime;

/**
 * One row of {@code GET /api/contests/{id}/leaderboard} (§44 paginated list).
 *
 * <p>{@code rank} is 1-based, computed as {@code pageable.getOffset() + indexInPage +
 * 1}. Ordering is {@code totalScore DESC, penaltySeconds ASC, lastAcceptedAt ASC,
 * studentId ASC} — the trailing {@code studentId} makes every tie fully deterministic
 * so a student's displayed rank never changes between page loads.
 */
public record ContestLeaderboardRowResponse(
        int rank,
        Long studentId,
        String studentName,
        String registerNumber,
        String departmentName,
        Integer totalScore,
        Integer problemsSolved,
        Integer penaltySeconds,
        LocalDateTime lastAcceptedAt) {}
