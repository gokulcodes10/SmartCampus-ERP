package smartcampus.dto;

import java.math.BigDecimal;
import smartcampus.entity.PerformanceCategory;

/**
 * One subject's full picture for one student — a full outer merge of that student's
 * {@link AttendanceSubjectSummary} and {@link SubjectGradeSummary} for the same
 * (subjectId, academicYear, semester), keyed on that triple. A subject with attendance
 * but no marks — or marks but no attendance — still appears here, with {@code null} on
 * the missing side; nothing is invented to fill the gap.
 *
 * <p>{@code classification} treats this single subject's {@code gradePoint} as its
 * "GPA" (both are on the same 0-10 scale) — see {@code
 * PerformanceClassifier#classify(BigDecimal, BigDecimal, BigDecimal)}.
 */
public record SubjectPerformanceRow(
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer credits,
        String academicYear,
        Integer semester,
        Long heldClasses,
        Long attendedClasses,
        BigDecimal attendancePercentage,
        Long examCount,
        BigDecimal totalObtained,
        BigDecimal totalMaximum,
        BigDecimal marksPercentage,
        String grade,
        BigDecimal gradePoint,
        Boolean passed,
        PerformanceCategory classification,
        String classificationColorHex) {}
