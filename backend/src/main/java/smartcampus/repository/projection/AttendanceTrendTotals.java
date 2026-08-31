package smartcampus.repository.projection;

/**
 * One calendar-month bucket of attendance totals, produced by {@code
 * AnalyticsAttendanceRepository.trendByStudent} / {@code trendByScope}. Getter names
 * must match those queries' JPQL {@code as} aliases exactly.
 *
 * <p>{@code periodYear}/{@code periodMonth} are wrapper {@link Integer}s (from HQL
 * {@code year()}/{@code month()}) so the caller assembles the {@code "YYYY-MM"} label in
 * Java; {@code heldClasses}/{@code attendedClasses} are wrapper {@link Long}s because
 * {@code sum()} over an empty group returns {@code null}, and a primitive getter would
 * NPE on unboxing that.
 */
public interface AttendanceTrendTotals {

    Integer getPeriodYear();

    Integer getPeriodMonth();

    Long getHeldClasses();

    Long getAttendedClasses();
}
