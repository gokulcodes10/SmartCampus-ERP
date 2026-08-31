package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One (academicYear, semester)'s credit-weighted GPA for one student — G7. {@code gpa}
 * is null when {@code gradedCredits} is zero (a student with no computable grade in
 * this semester is never reported as GPA 0.00).
 */
public record SemesterGradeSummary(
        String academicYear,
        Integer semester,
        int subjectCount,
        int gradedCredits,
        BigDecimal gpa,
        List<SubjectGradeSummary> subjects) {}
