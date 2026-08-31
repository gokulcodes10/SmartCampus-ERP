package smartcampus.dto;

import java.math.BigDecimal;
import smartcampus.entity.PerformanceCategory;

/**
 * One student's aggregated attendance, marks and GPA within a scoped cohort (a class or
 * an institution-wide overview) — every figure summed across only the subjects that
 * survived the {@code AnalyticsScopeResolver} tuple filter for this student. {@code gpa}
 * is the same credit-weighted computation {@code GradeCalculationService} uses
 * everywhere else, never an arithmetic mean. {@code classification} is {@code null}
 * when this student cannot yet be classified (see {@code PerformanceClassifier}) —
 * never defaulted to AT_RISK.
 */
public record CohortStudentRow(
        Long studentId,
        String registerNumber,
        String studentName,
        Long departmentId,
        String departmentName,
        Long courseId,
        String courseName,
        Long heldClasses,
        Long attendedClasses,
        BigDecimal attendancePercentage,
        Long examCount,
        BigDecimal totalObtained,
        BigDecimal totalMaximum,
        BigDecimal marksPercentage,
        int gradedCredits,
        BigDecimal gpa,
        PerformanceCategory classification,
        String classificationColorHex) {}
