package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestionCategory;

/**
 * POST /api/interview-questions/generate (STUDENT only). {@code difficulty} defaults to
 * {@link InterviewDifficulty#MEDIUM} when {@code null}; {@code count} defaults to 5 when
 * {@code null}. See {@code InterviewQuestionGenerationService} for the orchestration and
 * {@code InterviewPromptBuilder} for how these fields render into the provider prompt.
 */
public record InterviewQuestionGenerateRequest(
        @NotNull InterviewQuestionCategory category,
        InterviewDifficulty difficulty,
        @Size(max = 200) String topic,
        @Size(max = 150) String companyName,
        @Min(1) @Max(10) Integer count) {}
