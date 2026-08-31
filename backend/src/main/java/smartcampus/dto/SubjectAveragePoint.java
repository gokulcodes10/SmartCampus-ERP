package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One subject's attendance and marks totals aggregated across every student in a
 * cohort (a class or an institution-wide overview). {@code attendancePercentage} is
 * {@code null} when {@code heldClasses == 0}; {@code marksPercentage} is {@code null}
 * when no student in the cohort has a graded mark for this subject — never a
 * fabricated 0.
 */
public record SubjectAveragePoint(
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer credits,
        Long studentCount,
        Long heldClasses,
        Long attendedClasses,
        BigDecimal attendancePercentage,
        Long examCount,
        BigDecimal totalObtained,
        BigDecimal totalMaximum,
        BigDecimal marksPercentage) {}
