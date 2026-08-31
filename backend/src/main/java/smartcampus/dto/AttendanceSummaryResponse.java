package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A student's attendance summary — either scoped to one academic year/semester, or
 * (when both are omitted) across every subject the student has attendance records
 * for. {@code overallPercentage} is the credit-blind aggregate {@code
 * sum(attendedClasses) / sum(heldClasses)} over the whole set, NOT the arithmetic mean
 * of the per-subject percentages in {@code subjects} (G6) — a 1-class subject must not
 * weigh the same as a 40-class subject.
 */
public record AttendanceSummaryResponse(
        Long studentId,
        String registerNumber,
        String studentName,
        String academicYear,
        Integer semester,
        long totalRecords,
        long heldClasses,
        long attendedClasses,
        long cancelledClasses,
        BigDecimal overallPercentage,
        BigDecimal minimumPercentage,
        boolean lowAttendance,
        List<AttendanceSubjectSummary> subjects) {}
