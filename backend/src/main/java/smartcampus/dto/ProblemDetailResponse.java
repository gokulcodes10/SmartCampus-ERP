package smartcampus.dto;

import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.ProblemDifficulty;

/**
 * Full problem statement for {@code GET /api/problems/{id}}.
 *
 * <p>{@code sampleTestCases} contains SAMPLE cases only, for every caller including
 * ADMIN — hidden case input/output is never carried by this response, regardless of
 * role. The only route that ever returns a hidden case's input/expected output is the
 * ADMIN-only {@code GET /api/problems/{id}/test-cases}, which returns {@link
 * TestCaseResponse} rows instead.
 */
public record ProblemDetailResponse(
        Long id,
        String slug,
        String title,
        String description,
        String inputFormat,
        String outputFormat,
        String constraintsText,
        String sampleInput,
        String sampleOutput,
        ProblemDifficulty difficulty,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        List<String> tags,
        boolean published,
        Long createdById,
        String createdByName,
        List<SampleTestCaseResponse> sampleTestCases,
        long hiddenTestCaseCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
