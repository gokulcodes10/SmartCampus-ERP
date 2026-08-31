package smartcampus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import smartcampus.entity.ProblemDifficulty;

/**
 * Request body for {@code PUT /api/problems/{id}} (ADMIN only). Component list is
 * identical to {@link ProblemCreateRequest} — see its javadoc for why {@code
 * published} must be present here too.
 */
public record ProblemUpdateRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug must be lowercase alphanumeric words separated by hyphens.")
        String slug,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        String inputFormat,
        String outputFormat,
        String constraintsText,
        String sampleInput,
        String sampleOutput,
        @NotNull ProblemDifficulty difficulty,
        @NotNull @Min(100) @Max(15000) Integer timeLimitMs,
        @NotNull @Min(16384) @Max(512000) Integer memoryLimitKb,
        List<String> tags,
        Boolean published) {}
