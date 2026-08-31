package smartcampus.dto;

import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.ProblemDifficulty;

/**
 * Row shape for {@code GET /api/problems} (§44 paginated list). Carries the counts a
 * problem browser needs (how many samples to preview, how many hidden cases exist) but
 * none of the problem body text — that is {@link ProblemDetailResponse}'s job.
 */
public record ProblemSummaryResponse(
        Long id,
        String slug,
        String title,
        ProblemDifficulty difficulty,
        Integer timeLimitMs,
        Integer memoryLimitKb,
        List<String> tags,
        boolean published,
        long sampleTestCaseCount,
        long hiddenTestCaseCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
