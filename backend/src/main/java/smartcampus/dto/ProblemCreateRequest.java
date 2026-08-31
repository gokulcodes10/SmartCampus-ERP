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
 * Request body for {@code POST /api/problems} (ADMIN only).
 *
 * <p>{@code published} is declared here (nullable — {@code null} means "not
 * published", i.e. {@code false}) precisely because the admin UI has a publish toggle:
 * a request DTO that omitted this field would silently drop the toggle's value (the
 * Phase 3 "button that does nothing" trap) and Jackson would never even report it as
 * an error.
 */
public record ProblemCreateRequest(
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
