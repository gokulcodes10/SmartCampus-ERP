package smartcampus.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import smartcampus.entity.ExamType;

/**
 * Per-exam marks totals across every student with an entered mark, produced by {@code
 * AnalyticsMarksRepository.examTotals}. Getter names must match that query's JPQL
 * {@code as} aliases exactly.
 *
 * <p>Every numeric getter is a wrapper type because the aggregate functions ({@code
 * count}/{@code sum}/{@code max}/{@code min}) return {@code null} when no {@link
 * smartcampus.entity.Marks} row exists for the exam within the requested scope, and a
 * primitive getter would NPE on unboxing that.
 */
public interface ExamMarksTotals {

    Long getExamId();

    String getTitle();

    ExamType getExamType();

    LocalDate getExamDate();

    BigDecimal getMaximumMarks();

    Long getMarksEnteredCount();

    BigDecimal getTotalObtained();

    BigDecimal getHighestObtained();

    BigDecimal getLowestObtained();
}
