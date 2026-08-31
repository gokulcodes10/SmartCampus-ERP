package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One subject's grade within one (academicYear, semester) for one student — G7. {@code
 * percentage}/{@code grade}/{@code gradePoint}/{@code passed} are all null when no
 * {@code GradeBand} matches the computed percentage (never a fabricated letter), or when
 * {@code totalMaximum} is zero/null.
 */
public record SubjectGradeSummary(
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer credits,
        String academicYear,
        Integer semester,
        long examCount,
        BigDecimal totalObtained,
        BigDecimal totalMaximum,
        BigDecimal percentage,
        String grade,
        BigDecimal gradePoint,
        Boolean passed) {}
