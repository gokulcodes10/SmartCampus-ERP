package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestionCategory;

/**
 * ADMIN-only update payload for a global (curated) interview question. Identical field
 * list to {@link InterviewQuestionCreateRequest}; {@code difficulty} of {@code null}
 * defaults to {@link InterviewDifficulty#MEDIUM}.
 */
public record InterviewQuestionUpdateRequest(
        @NotNull InterviewQuestionCategory category,
        InterviewDifficulty difficulty,
        @NotBlank String question,
        String answer,
        String explanation,
        @Size(max = 150) String companyName,
        @Size(max = 255) String tags) {}
