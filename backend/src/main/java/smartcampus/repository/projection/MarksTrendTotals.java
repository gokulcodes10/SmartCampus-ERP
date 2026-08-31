package smartcampus.repository.projection;

import java.math.BigDecimal;

/**
 * One calendar-month bucket of marks totals, produced by {@code
 * AnalyticsMarksRepository.trendByStudent} / {@code trendByScope}. Getter names must
 * match those queries' JPQL {@code as} aliases exactly.
 *
 * <p>{@code periodYear}/{@code periodMonth} are wrapper {@link Integer}s (from HQL
 * {@code year()}/{@code month()}) so the caller assembles the {@code "YYYY-MM"} label in
 * Java; {@code totalObtained}/{@code totalMaximum} are wrapper {@link BigDecimal}s
 * because {@code sum()} over an empty group returns {@code null}.
 */
public interface MarksTrendTotals {

    Integer getPeriodYear();

    Integer getPeriodMonth();

    Long getExamCount();

    BigDecimal getTotalObtained();

    BigDecimal getTotalMaximum();
}
