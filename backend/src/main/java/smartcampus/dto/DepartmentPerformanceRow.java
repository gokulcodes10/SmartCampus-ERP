package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One department's aggregated attendance, marks and average GPA within an institution
 * overview. {@code averageGpa} is the arithmetic mean of the NON-NULL per-student GPAs
 * in this department (documented as a mean of GPAs, not credit-weighted across
 * students — credit-weighting across different students is meaningless), {@code null}
 * when no student in the department has a computable GPA.
 */
public record DepartmentPerformanceRow(
        Long departmentId,
        String departmentCode,
        String departmentName,
        Long studentCount,
        BigDecimal attendancePercentage,
        BigDecimal marksPercentage,
        BigDecimal averageGpa) {}
