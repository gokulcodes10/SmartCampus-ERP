package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One subject's performance snapshot inside a student's academic context: grade and
 * attendance for one (academicYear, semester, subject) combination. Assembled by the
 * context-building service. Every percentage/grade field is null when it cannot be
 * computed from real rows (§69 null policy) — never 0.00, never "N/A", never an
 * invented letter grade.
 */
public record AISubjectPerformanceResponse(
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer credits,
        String academicYear,
        Integer semester,
        BigDecimal marksPercentage,
        String grade,
        BigDecimal attendancePercentage,
        boolean lowAttendance) {}
