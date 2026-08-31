package smartcampus.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;

/**
 * Request body for {@code PUT /api/exams/{id}}.
 *
 * <p>Deliberately does NOT carry {@code subjectId}/{@code academicYear}/{@code
 * semester}/{@code section} — that scope tuple is immutable after creation. Moving an
 * exam into a different subject, year, semester or section after the write was already
 * authorized against the original tuple would be an authorization bypass: a faculty
 * member assigned only to section A could otherwise create an exam in section A and
 * then "edit" it into section B. If a client sends those fields anyway, Jackson simply
 * drops them since this record does not declare them — never add them here.
 */
public record ExamUpdateRequest(
        @NotBlank(message = "Title is required.") @Size(max = 150, message = "Title must be at most 150 characters.")
                String title,
        @NotNull(message = "Exam type is required.") ExamType examType,
        @NotNull(message = "Exam date is required.") LocalDate examDate,
        @NotNull(message = "Maximum marks is required.")
                @DecimalMin(value = "0.01", message = "Maximum marks must be greater than zero.")
                @DecimalMax(value = "1000.00", message = "Maximum marks must be at most 1000.")
                @Digits(integer = 4, fraction = 2, message = "Maximum marks may have at most 4 integer and 2 fraction digits.")
                BigDecimal maximumMarks,
        @NotNull(message = "Status is required.") ExamStatus status) {}
