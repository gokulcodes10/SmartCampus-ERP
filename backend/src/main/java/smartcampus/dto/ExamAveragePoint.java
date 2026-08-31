package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import smartcampus.entity.ExamType;

/**
 * One exam's marks totals across every student who has a mark for it (CANCELLED exams
 * never appear — G7). {@code averageObtained} is {@code totalObtained /
 * marksEnteredCount}, scale 2 HALF_UP, {@code null} when {@code marksEnteredCount == 0}
 * — never a fabricated 0. {@code averagePercentage} is {@code
 * GradeCalculationService.percentage(averageObtained, maximumMarks)}.
 */
public record ExamAveragePoint(
        Long examId,
        String title,
        ExamType examType,
        LocalDate examDate,
        BigDecimal maximumMarks,
        Long marksEnteredCount,
        BigDecimal totalObtained,
        BigDecimal averageObtained,
        BigDecimal averagePercentage,
        BigDecimal highestObtained,
        BigDecimal lowestObtained) {}
