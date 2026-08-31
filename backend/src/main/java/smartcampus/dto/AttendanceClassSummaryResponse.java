package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The per-student attendance breakdown for one taught class (subject, academic year,
 * semester, section) — the faculty-facing counterpart of
 * {@code AttendanceSummaryResponse}. Only students with at least one attendance row
 * for this exact tuple appear in {@code entries}.
 */
public record AttendanceClassSummaryResponse(
        Long subjectId,
        String subjectCode,
        String subjectName,
        String academicYear,
        Integer semester,
        String section,
        BigDecimal minimumPercentage,
        int studentCount,
        int lowAttendanceCount,
        List<AttendanceClassSummaryEntry> entries) {}
