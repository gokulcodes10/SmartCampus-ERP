package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One month's marks totals in a Phase 5 analytics trend line, excluding CANCELLED
 * exams. {@code marksPercentage} is {@code null} when {@code totalMaximum} is
 * null/zero (G7) — never a fabricated 0.
 */
public record MarksTrendPoint(
        String period,
        LocalDate periodStart,
        Long examCount,
        BigDecimal totalObtained,
        BigDecimal totalMaximum,
        BigDecimal marksPercentage) {}
