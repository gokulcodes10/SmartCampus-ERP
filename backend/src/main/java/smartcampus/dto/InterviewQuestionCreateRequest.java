package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.InterviewDifficulty;
import smartcampus.entity.InterviewQuestionCategory;

/**
 * ADMIN-only creation payload for a global (curated) interview question. {@code
 * difficulty} is optional — a {@code null} value defaults to {@link
 * InterviewDifficulty#MEDIUM}.
 */
public record InterviewQuestionCreateRequest(
        @NotNull InterviewQuestionCategory category,
        InterviewDifficulty difficulty,
        @NotBlank String question,
        String answer,
        String explanation,
        @Size(max = 150) String companyName,
        @Size(max = 255) String tags) {}
