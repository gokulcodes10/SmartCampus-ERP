package smartcampus.dto;

import java.math.BigDecimal;

/** One student's attendance totals within a class summary ({@code AttendanceClassSummaryResponse}). */
public record AttendanceClassSummaryEntry(
        Long studentId,
        String registerNumber,
        String studentName,
        long totalRecords,
        long heldClasses,
        long attendedClasses,
        long cancelledClasses,
        BigDecimal attendancePercentage,
        boolean lowAttendance) {}
