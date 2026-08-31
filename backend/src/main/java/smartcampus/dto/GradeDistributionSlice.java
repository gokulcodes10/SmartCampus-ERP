package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One letter grade's share of a set of subject-grade results, sourced from a real
 * {@code GradeBand} row (never a literal grade letter or boundary in Java) — see
 * {@code GradeBandRepository#findAllByOrderByMinPercentageDesc}. A band with
 * {@code count == 0} is still included, so a chart's category axis stays stable.
 */
public record GradeDistributionSlice(
        String grade, BigDecimal gradePoint, BigDecimal minPercentage, BigDecimal maxPercentage, long count) {}
