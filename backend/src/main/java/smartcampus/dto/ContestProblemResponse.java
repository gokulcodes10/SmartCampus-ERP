package smartcampus.dto;

import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.SubmissionStatus;

/**
 * One problem's membership in a contest, as shown in {@code ContestDetailResponse.problems}.
 *
 * <p>{@code label} is the usual A/B/C rendering of {@code ordinal} (1→"A", 2→"B", ...;
 * beyond 26 the ordinal's decimal string is used instead).
 *
 * <p>{@code myBestStatus}/{@code myAttempts} are computed against the CALLER's own
 * submissions for this problem within this contest: {@code myBestStatus} is {@code
 * ACCEPTED} if the caller ever solved it here, else the status of their latest attempt,
 * else {@code null}. Both are {@code null}/{@code 0} for a non-student caller.
 */
public record ContestProblemResponse(
        Long id,
        Long contestId,
        Long problemId,
        String problemTitle,
        ProblemDifficulty difficulty,
        Integer ordinal,
        String label,
        Integer points,
        SubmissionStatus myBestStatus,
        int myAttempts) {}
