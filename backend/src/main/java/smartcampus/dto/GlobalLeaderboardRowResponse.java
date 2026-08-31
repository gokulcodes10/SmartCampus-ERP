package smartcampus.dto;

import java.time.LocalDateTime;

/**
 * One row of {@code GET /api/leaderboard/global} (§44 paginated list).
 *
 * <p>{@code problemsSolved} is {@code COUNT(DISTINCT problem_id)} over the student's
 * ACCEPTED submissions; {@code totalScore} sums each DISTINCT solved problem's
 * difficulty points exactly once — solving the same problem twice must never
 * double-score it. Ordering is {@code totalScore DESC, problemsSolved DESC,
 * lastAcceptedAt ASC, studentId ASC}.
 */
public record GlobalLeaderboardRowResponse(
        int rank,
        Long studentId,
        String studentName,
        String registerNumber,
        String departmentName,
        long problemsSolved,
        long totalScore,
        LocalDateTime lastAcceptedAt) {}
