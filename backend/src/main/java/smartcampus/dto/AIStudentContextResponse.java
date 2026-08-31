package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The real academic snapshot the AI assistant grounds itself in, returned by {@code GET
 * /api/ai/context} so a student can see exactly what the assistant knows about them.
 * Assembled by {@code AIContextService}.
 *
 * <p>{@code upcomingExams} reuses the existing {@link ExamResponse} shape rather than
 * defining an AI-specific one. Every percentage/CGPA field is null when it cannot be
 * computed from real rows (§69 null policy) — never 0.00, never "N/A". {@code
 * hasAcademicData} is false only when the student has no marks/attendance/exam rows at
 * all (e.g. a newly activated student).
 */
public record AIStudentContextResponse(
        Long studentId,
        String registerNumber,
        String studentName,
        String departmentName,
        String courseName,
        Integer currentSemester,
        String section,
        BigDecimal cgpa,
        Integer totalGradedCredits,
        BigDecimal overallAttendancePercentage,
        BigDecimal minimumAttendancePercentage,
        boolean lowAttendance,
        List<AIWeakSubjectResponse> weakSubjects,
        List<AISubjectPerformanceResponse> subjects,
        List<ExamResponse> upcomingExams,
        boolean hasAcademicData) {}
