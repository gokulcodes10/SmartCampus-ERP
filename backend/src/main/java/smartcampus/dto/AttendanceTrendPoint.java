package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One month's attendance totals in a Phase 5 analytics trend line. {@code period} is
 * {@code "YYYY-MM"}, assembled in Java from the database's {@code year()}/{@code
 * month()} grouping — never from {@code DATE_FORMAT}/{@code function()}. {@code
 * attendancePercentage} is {@code null} when {@code heldClasses == 0} (G6) — a month
 * with no held classes is never reported at 0%. A month with no attendance rows at all
 * is simply absent from the trend list; it is never invented as a zero point.
 */
public record AttendanceTrendPoint(
        String period,
        LocalDate periodStart,
        Long heldClasses,
        Long attendedClasses,
        BigDecimal attendancePercentage) {}
