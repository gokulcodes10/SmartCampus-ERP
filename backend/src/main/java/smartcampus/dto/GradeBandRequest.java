package smartcampus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request body for {@code POST}/{@code PUT /api/grade-bands} — admin-configurable
 * percentage-&gt;grade-&gt;grade-point mapping (G7). {@code passGrade} is deliberately a
 * boxed {@link Boolean} with {@code @NotNull}: a primitive {@code boolean} would let an
 * omitted field silently default to {@code false}, marking a passing band as a fail.
 */
public record GradeBandRequest(
        @NotBlank(message = "Grade is required.") @Size(max = 5, message = "Grade must be at most 5 characters.")
                String grade,
        @NotNull(message = "Minimum percentage is required.")
                @DecimalMin(value = "0.00", message = "Minimum percentage cannot be negative.")
                @DecimalMax(value = "100.00", message = "Minimum percentage cannot exceed 100.")
                @Digits(integer = 3, fraction = 2, message = "Minimum percentage may have at most 3 integer and 2 fraction digits.")
                BigDecimal minPercentage,
        @NotNull(message = "Maximum percentage is required.")
                @DecimalMin(value = "0.00", message = "Maximum percentage cannot be negative.")
                @DecimalMax(value = "100.00", message = "Maximum percentage cannot exceed 100.")
                @Digits(integer = 3, fraction = 2, message = "Maximum percentage may have at most 3 integer and 2 fraction digits.")
                BigDecimal maxPercentage,
        @NotNull(message = "Grade point is required.")
                @DecimalMin(value = "0.00", message = "Grade point cannot be negative.")
                @DecimalMax(value = "10.00", message = "Grade point cannot exceed 10.")
                @Digits(integer = 2, fraction = 2, message = "Grade point may have at most 2 integer and 2 fraction digits.")
                BigDecimal gradePoint,
        @NotNull(message = "passGrade is required.") Boolean passGrade,
        @Size(max = 100, message = "Description must be at most 100 characters.") String description) {}
