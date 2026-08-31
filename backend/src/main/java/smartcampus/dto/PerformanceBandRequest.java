package smartcampus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request body for {@code PUT /api/performance-bands/{id}} — admin-configurable thresholds for
 * one {@link smartcampus.entity.PerformanceCategory} (§60). {@code category} and {@code
 * displayOrder} are deliberately ABSENT: the four categories and their priority order are a
 * closed set fixed by the database CHECK constraint and the {@code PerformanceCategory} enum —
 * only the thresholds and display colour are configurable.
 */
public record PerformanceBandRequest(
        @NotNull(message = "Minimum marks percentage is required.")
                @DecimalMin(value = "0.00", message = "Minimum marks percentage cannot be negative.")
                @DecimalMax(value = "100.00", message = "Minimum marks percentage cannot exceed 100.")
                @Digits(
                        integer = 3,
                        fraction = 2,
                        message = "Minimum marks percentage may have at most 3 integer and 2 fraction digits.")
                BigDecimal minMarksPercentage,
        @NotNull(message = "Minimum attendance percentage is required.")
                @DecimalMin(value = "0.00", message = "Minimum attendance percentage cannot be negative.")
                @DecimalMax(value = "100.00", message = "Minimum attendance percentage cannot exceed 100.")
                @Digits(
                        integer = 3,
                        fraction = 2,
                        message = "Minimum attendance percentage may have at most 3 integer and 2 fraction digits.")
                BigDecimal minAttendancePercentage,
        // Nullable on purpose: null means "this band imposes no GPA requirement", never "unknown".
        @DecimalMin(value = "0.00", message = "Minimum GPA cannot be negative.")
                @DecimalMax(value = "10.00", message = "Minimum GPA cannot exceed 10.")
                @Digits(integer = 2, fraction = 2, message = "Minimum GPA may have at most 2 integer and 2 fraction digits.")
                BigDecimal minGpa,
        @NotBlank(message = "Colour is required.")
                @Size(max = 7, message = "Colour must be at most 7 characters.")
                @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour must be a 6-digit hex code like #16A34A.")
                String colorHex,
        @Size(max = 150, message = "Description must be at most 150 characters.") String description) {}
