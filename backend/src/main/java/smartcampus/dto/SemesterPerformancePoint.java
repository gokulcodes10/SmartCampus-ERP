package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One (academicYear, semester)'s aggregated attendance, marks and average GPA across an
 * institution overview's cohort. {@code averageGpa} is the arithmetic mean of the
 * NON-NULL per-student-per-semester GPAs in this bucket, {@code null} when none.
 */
public record SemesterPerformancePoint(
        String academicYear,
        Integer semester,
        Long studentCount,
        BigDecimal attendancePercentage,
        BigDecimal marksPercentage,
        BigDecimal averageGpa) {}
