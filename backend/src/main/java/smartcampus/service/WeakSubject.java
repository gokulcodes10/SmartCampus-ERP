package smartcampus.service;

import java.math.BigDecimal;

/**
 * One subject identified as academically weak for a student — low marks, low
 * attendance, or both. Computed by {@link AIContextService} from real
 * {@code AcademicResultResponse}/{@code AttendanceSummaryResponse} rows only; a subject
 * whose percentage is {@code null} (nothing graded, or every class cancelled) is never
 * counted as weak (§69 — absence of data is never evidence of weakness).
 *
 * <p>{@code reason} states plainly which threshold was crossed and with what number,
 * e.g. {@code "marks 41.5% is below 50%"}, {@code "attendance 62.0% is below 75%"}, or
 * both joined with {@code "; "}.
 */
public record WeakSubject(
        Long subjectId,
        String subjectCode,
        String subjectName,
        BigDecimal marksPercentage,
        BigDecimal attendancePercentage,
        String reason) {}
