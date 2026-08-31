package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import smartcampus.entity.ExamType;

/**
 * Response representation of a {@code Marks} row, safe to return from bulk-entry,
 * entry-sheet-adjacent, detail, list and update endpoints.
 *
 * <p>{@code percentage}/{@code grade}/{@code gradePoint} here are this single exam's
 * score mapped through the grade bands (obtained/maximum for THIS exam only) — not the
 * subject-level aggregate {@code SubjectGradeSummary} computes. Built by {@code
 * MarksService} via {@code GradeCalculationService} rather than a static {@code from}
 * factory, because the grade band lookup is not a property of the entity alone.
 */
public record MarksResponse(
        Long id,
        Long examId,
        String examTitle,
        ExamType examType,
        LocalDate examDate,
        Long subjectId,
        String subjectCode,
        String subjectName,
        Integer subjectCredits,
        String academicYear,
        Integer semester,
        String section,
        Long studentId,
        String studentRegisterNumber,
        String studentName,
        BigDecimal marksObtained,
        BigDecimal maximumMarks,
        BigDecimal percentage,
        String grade,
        BigDecimal gradePoint,
        String remarks,
        Long enteredByFacultyId,
        String enteredByFacultyName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
