package smartcampus.service;

import java.math.BigDecimal;
import java.util.List;
import smartcampus.dto.AcademicResultResponse;
import smartcampus.dto.AttendanceSummaryResponse;
import smartcampus.dto.ExamResponse;

/**
 * The full, real academic record of one student, assembled by {@link
 * AIContextService#buildFor(smartcampus.entity.User)} from live rows only (§69 — never
 * invented, defaulted or averaged into existence) and rendered into the AI system
 * prompt by {@link AIPromptBuilder#systemPrompt}.
 *
 * <p>{@code marks} and {@code attendance} are exactly what {@code MarksService
 * #mySummary} and {@code AttendanceService#mySummary} already compute (called with a
 * null academic year/semester, i.e. across the student's whole record) — this record
 * does not restate their null policy, it only carries the result.
 *
 * <p>{@code hasAcademicData} is {@code false} only when the student has no graded
 * subject, no attendance record and no upcoming exam — every other case leaves it
 * {@code true} even when individual fields inside {@code marks}/{@code attendance} are
 * still null.
 */
public record StudentAcademicContext(
        Long studentId,
        String registerNumber,
        String studentName,
        String departmentName,
        String courseName,
        Integer currentSemester,
        String section,
        AcademicResultResponse marks,
        AttendanceSummaryResponse attendance,
        List<WeakSubject> weakSubjects,
        List<ExamResponse> upcomingExams,
        BigDecimal minimumAttendancePercentage,
        boolean hasAcademicData) {}
