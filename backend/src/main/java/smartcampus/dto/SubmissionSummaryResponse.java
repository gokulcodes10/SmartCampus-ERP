package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.SubmissionStatus;

/**
 * Row shape for {@code GET /api/coding/submissions} (§44 paginated list). {@code
 * contestId}/{@code contestTitle} are {@code null} for a practice submission.
 *
 * <p>{@code submittedAt} is mapped from the entity's {@code createdAt} — there is no
 * separate {@code submitted_at} column (see V7__coding.sql); {@code created_at} IS the
 * submission time and is never rewritten after insert.
 */
public record SubmissionSummaryResponse(
        Long id,
        Long problemId,
        String problemTitle,
        ProblemDifficulty problemDifficulty,
        Long studentId,
        String studentName,
        String registerNumber,
        Long contestId,
        String contestTitle,
        ProgrammingLanguage language,
        SubmissionStatus status,
        Integer passedTestCases,
        Integer totalTestCases,
        Integer score,
        Integer maxScore,
        Integer executionTimeMs,
        Integer memoryKb,
        Integer failedTestCaseOrdinal,
        LocalDateTime submittedAt) {}
