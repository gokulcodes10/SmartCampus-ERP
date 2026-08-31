package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import smartcampus.entity.ProgrammingLanguage;

/**
 * Request body for {@code POST /api/coding/run} (free-form playground execution) and
 * {@code POST /api/coding/problems/{problemId}/run} (sample-case run — {@code stdin}
 * is ignored on that route since the samples supply their own input).
 */
public record RunRequest(
        @NotNull ProgrammingLanguage language, @NotBlank String sourceCode, String stdin) {}
