package smartcampus.dto;

import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.ProblemDifficulty;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.SubmissionStatus;

/**
 * Full detail for {@code GET /api/coding/submissions/{id}} and the response of {@code
 * POST /api/coding/submissions}. Carries every field of {@link SubmissionSummaryResponse}
 * plus the source code, compiler diagnostics and the per-test-case breakdown. Records
 * cannot extend one another, so the shared fields are repeated rather than inherited.
 */
public record SubmissionDetailResponse(
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
        LocalDateTime submittedAt,
        String sourceCode,
        String compileOutput,
        String errorMessage,
        LocalDateTime judgedAt,
        List<SubmissionTestResultResponse> testResults) {}
