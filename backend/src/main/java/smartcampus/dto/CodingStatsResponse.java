package smartcampus.dto;

/** Response for {@code GET /api/coding/stats/me} — the caller's own practice/contest activity. */
public record CodingStatsResponse(
        long totalSubmissions,
        long acceptedSubmissions,
        long problemsAttempted,
        long problemsSolved,
        long solvedEasy,
        long solvedMedium,
        long solvedHard) {}
