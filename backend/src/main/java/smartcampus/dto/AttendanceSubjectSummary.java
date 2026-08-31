package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One subject's attendance totals within a student's summary, per the G6 attendance
 * rule ({@code AttendanceService} computes {@code attendancePercentage} — never a
 * literal 0 — as {@code null} when {@code heldClasses} is zero, including when every
 * session for the subject was {@code CANCELLED}).
 */
public record AttendanceSubjectSummary(
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer credits,
        String academicYear,
        Integer semester,
        long totalRecords,
        long heldClasses,
        long attendedClasses,
        long cancelledClasses,
        BigDecimal attendancePercentage,
        boolean lowAttendance) {}
