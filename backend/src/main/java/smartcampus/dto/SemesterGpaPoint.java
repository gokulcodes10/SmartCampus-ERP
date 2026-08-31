package smartcampus.dto;

import java.math.BigDecimal;

/**
 * One (academicYear, semester)'s credit-weighted GPA point in a student's GPA trend —
 * one per element of {@link AcademicResultResponse#semesters()}, in the same order.
 * {@code gpa} is {@code null} when {@code gradedCredits == 0}, never a fabricated 0.00.
 */
public record SemesterGpaPoint(
        String academicYear, Integer semester, int subjectCount, int gradedCredits, BigDecimal gpa) {}
