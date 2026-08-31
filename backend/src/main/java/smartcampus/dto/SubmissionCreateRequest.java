package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.ProgrammingLanguage;

/**
 * Request body for {@code POST /api/coding/submissions}. {@code contestId} is {@code
 * null} for a practice submission from the playground, non-null for a contest attempt.
 */
public record SubmissionCreateRequest(
        @NotNull Long problemId,
        @NotNull ProgrammingLanguage language,
        @NotBlank @Size(max = 100000) String sourceCode,
        Long contestId) {}
