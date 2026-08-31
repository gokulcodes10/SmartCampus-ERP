package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/ai/practice-questions}: ask for practice questions. */
public record AIPracticeQuestionsRequest(
        @NotBlank @Size(max = 200) String topic,
        Long subjectId,
        @Min(1) @Max(20) Integer count,
        AIDifficulty difficulty) {}
