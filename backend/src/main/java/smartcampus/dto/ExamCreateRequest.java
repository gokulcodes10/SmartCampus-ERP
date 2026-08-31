package smartcampus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import smartcampus.entity.ExamType;

/**
 * Request body for {@code POST /api/exams} — schedules a new exam (G4) or, when {@code
 * examType} is {@code ASSIGNMENT}, records an assignment (G5, which has no separate
 * submission module).
 *
 * <p>The {@code (subjectId, academicYear, semester, section)} tuple here is exactly the
 * tuple {@code ScopedWriteAuthorizer.requireScopedWrite} checks before this request is
 * persisted — it becomes the exam's permanent scope, immutable after creation (see
 * {@link ExamUpdateRequest}).
 */
public record ExamCreateRequest(
        @NotNull(message = "Subject is required.") Long subjectId,
        @NotBlank(message = "Title is required.") @Size(max = 150, message = "Title must be at most 150 characters.")
                String title,
        @NotNull(message = "Exam type is required.") ExamType examType,
        @NotBlank(message = "Academic year is required.")
                @Pattern(regexp = "\\d{4}-\\d{4}", message = "Academic year must be in the form \"2025-2026\".")
                String academicYear,
        @NotNull(message = "Semester is required.") @Positive(message = "Semester must be positive.")
                Integer semester,
        @NotBlank(message = "Section is required.") @Size(max = 10, message = "Section must be at most 10 characters.")
                String section,
        @NotNull(message = "Exam date is required.") LocalDate examDate,
        @NotNull(message = "Maximum marks is required.")
                @DecimalMin(value = "0.01", message = "Maximum marks must be greater than zero.")
                @DecimalMax(value = "1000.00", message = "Maximum marks must be at most 1000.")
                @Digits(integer = 4, fraction = 2, message = "Maximum marks may have at most 4 integer and 2 fraction digits.")
                BigDecimal maximumMarks) {}
