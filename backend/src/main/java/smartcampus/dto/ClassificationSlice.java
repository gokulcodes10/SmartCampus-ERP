package smartcampus.dto;

import java.math.BigDecimal;
import smartcampus.entity.PerformanceCategory;

/**
 * One {@code PerformanceCategory}'s share of a cohort, sourced from a real {@code
 * PerformanceBand} row in {@code display_order} — never a literal category or colour in
 * Java. A band with {@code studentCount == 0} is still included, so a chart's category
 * axis stays stable. {@code shareOfCohort} is {@code null} when the cohort has no
 * classified student at all (never a fabricated 0.00).
 */
public record ClassificationSlice(
        PerformanceCategory category,
        String colorHex,
        String description,
        long studentCount,
        BigDecimal shareOfCohort) {}
