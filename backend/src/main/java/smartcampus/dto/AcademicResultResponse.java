package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A student's full graded academic record — every (academicYear, semester) with a
 * computable GPA, plus the CGPA across all of them (G7). {@code cgpa} is null when
 * nothing is graded yet, never a fabricated 0.00.
 */
public record AcademicResultResponse(
        Long studentId,
        String registerNumber,
        String studentName,
        int totalGradedCredits,
        BigDecimal cgpa,
        List<SemesterGradeSummary> semesters) {}
