package smartcampus.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AcademicResultResponse;
import smartcampus.dto.AISubjectPerformanceResponse;
import smartcampus.dto.AIStudentContextResponse;
import smartcampus.dto.AIWeakSubjectResponse;
import smartcampus.dto.AttendanceSubjectSummary;
import smartcampus.dto.AttendanceSummaryResponse;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.SemesterGradeSummary;
import smartcampus.dto.SubjectGradeSummary;
import smartcampus.entity.Role;
import smartcampus.entity.Student;
import smartcampus.entity.User;

/**
 * Assembles the ONE real, live academic record an AI turn is allowed to be grounded in
 * (§69 — every number here comes from a database row for THIS student; nothing is
 * invented, defaulted or averaged into existence).
 *
 * <p>Reuses {@code MarksService#mySummary}, {@code AttendanceService#mySummary} and
 * {@code ExamService#upcoming} rather than querying their repositories directly — those
 * three already enforce own-student access and already carry the G6/G7 null policy, so
 * this class only has to combine what they return.
 *
 * <p>Restricted to STUDENT callers only (GET /api/ai/context, and every feature that
 * grounds itself through {@link #buildFor}) — a caller with no STUDENT role is rejected
 * before any query runs.
 */
@Service
public class AIContextService {

    private final MarksService marksService;
    private final AttendanceService attendanceService;
    private final ExamService examService;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;

    @Value("${smartcampus.ai.weak-subject.marks-threshold:50}")
    private BigDecimal marksThreshold;

    @Value("${smartcampus.attendance.minimum-percentage:75}")
    private BigDecimal attendanceThreshold;

    @Value("${smartcampus.ai.context.upcoming-exam-limit:10}")
    private int upcomingExamLimit;

    public AIContextService(
            MarksService marksService,
            AttendanceService attendanceService,
            ExamService examService,
            ScopedWriteAuthorizer scopedWriteAuthorizer) {
        this.marksService = marksService;
        this.attendanceService = attendanceService;
        this.examService = examService;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
    }

    /** The full record, for {@link AIPromptBuilder#systemPrompt} and other grounding. */
    @Transactional(readOnly = true)
    public StudentAcademicContext buildFor(User caller) {
        Assembled a = assemble(caller);
        return new StudentAcademicContext(
                a.student.getId(),
                a.student.getRegisterNumber(),
                a.student.getUser().getFullName(),
                a.student.getDepartment() != null ? a.student.getDepartment().getName() : null,
                a.student.getCourse() != null ? a.student.getCourse().getName() : null,
                a.student.getCurrentSemester(),
                a.student.getSection(),
                a.marks,
                a.attendance,
                a.weakSubjects,
                a.upcomingExams,
                a.attendance.minimumPercentage(),
                a.hasAcademicData);
    }

    /** The same data rendered for {@code GET /api/ai/context}, directly visible to the student. */
    @Transactional(readOnly = true)
    public AIStudentContextResponse snapshotFor(User caller) {
        Assembled a = assemble(caller);

        List<AIWeakSubjectResponse> weakSubjects =
                a.weakSubjects.stream()
                        .map(
                                w ->
                                        new AIWeakSubjectResponse(
                                                w.subjectId(),
                                                w.subjectCode(),
                                                w.subjectName(),
                                                w.marksPercentage(),
                                                w.attendancePercentage(),
                                                w.reason()))
                        .toList();

        List<AISubjectPerformanceResponse> subjects =
                a.subjectRows.stream().map(this::toSubjectPerformanceResponse).toList();

        return new AIStudentContextResponse(
                a.student.getId(),
                a.student.getRegisterNumber(),
                a.student.getUser().getFullName(),
                a.student.getDepartment() != null ? a.student.getDepartment().getName() : null,
                a.student.getCourse() != null ? a.student.getCourse().getName() : null,
                a.student.getCurrentSemester(),
                a.student.getSection(),
                a.marks.cgpa(),
                a.marks.totalGradedCredits(),
                a.attendance.overallPercentage(),
                a.attendance.minimumPercentage(),
                a.attendance.lowAttendance(),
                weakSubjects,
                subjects,
                a.upcomingExams,
                a.hasAcademicData);
    }

    // ------------------------------------------------------------------
    // Shared assembly
    // ------------------------------------------------------------------

    /** One (subject, academicYear, semester) row, merging the marks half and the attendance half. */
    private record SubjectRow(
            Long subjectId,
            String subjectCode,
            String subjectName,
            Integer credits,
            String academicYear,
            Integer semester,
            BigDecimal marksPercentage,
            String grade,
            BigDecimal attendancePercentage) {}

    private record Assembled(
            Student student,
            AcademicResultResponse marks,
            AttendanceSummaryResponse attendance,
            List<ExamResponse> upcomingExams,
            List<SubjectRow> subjectRows,
            List<WeakSubject> weakSubjects,
            boolean hasAcademicData) {}

    private Assembled assemble(User caller) {
        Student student = requireStudentCaller(caller);
        AcademicResultResponse marks = marksService.mySummary(caller, null, null);
        AttendanceSummaryResponse attendance = attendanceService.mySummary(caller, null, null);
        List<ExamResponse> upcomingExams = examService.upcoming(caller, upcomingExamLimit);

        List<SubjectRow> subjectRows = mergeSubjectRows(marks, attendance);
        List<WeakSubject> weakSubjects = computeWeakSubjects(subjectRows);

        boolean hasMarks =
                marks.semesters() != null
                        && marks.semesters().stream().anyMatch(s -> !s.subjects().isEmpty());
        boolean hasAttendance = attendance.subjects() != null && !attendance.subjects().isEmpty();
        boolean hasUpcoming = !upcomingExams.isEmpty();
        boolean hasAcademicData = hasMarks || hasAttendance || hasUpcoming;

        return new Assembled(student, marks, attendance, upcomingExams, subjectRows, weakSubjects, hasAcademicData);
    }

    private Student requireStudentCaller(User caller) {
        if (caller == null || caller.getRole() != Role.STUDENT) {
            throw new AccessDeniedException("The AI assistant is available to student accounts only.");
        }
        return scopedWriteAuthorizer.requireOwnStudent(caller);
    }

    /**
     * Merges the marks-side per-(subject, academicYear, semester) rows with the
     * attendance-side rows sharing that exact tuple. A subject present on only one
     * side keeps a null on the other — this is a union, never a fabricated pairing.
     */
    private List<SubjectRow> mergeSubjectRows(AcademicResultResponse marks, AttendanceSummaryResponse attendance) {
        Map<String, SubjectRow> byKey = new LinkedHashMap<>();

        for (SemesterGradeSummary semester : marks.semesters()) {
            for (SubjectGradeSummary sg : semester.subjects()) {
                String key = key(sg.subjectId(), sg.academicYear(), sg.semester());
                byKey.putIfAbsent(
                        key,
                        new SubjectRow(
                                sg.subjectId(),
                                sg.subjectCode(),
                                sg.subjectName(),
                                sg.credits(),
                                sg.academicYear(),
                                sg.semester(),
                                sg.percentage(),
                                sg.grade(),
                                null));
            }
        }

        for (AttendanceSubjectSummary as : attendance.subjects()) {
            String key = key(as.subjectId(), as.academicYear(), as.semester());
            SubjectRow existing = byKey.get(key);
            if (existing == null) {
                byKey.put(
                        key,
                        new SubjectRow(
                                as.subjectId(),
                                as.subjectCode(),
                                as.subjectName(),
                                as.credits(),
                                as.academicYear(),
                                as.semester(),
                                null,
                                null,
                                as.attendancePercentage()));
            } else {
                byKey.put(
                        key,
                        new SubjectRow(
                                existing.subjectId(),
                                existing.subjectCode(),
                                existing.subjectName(),
                                existing.credits(),
                                existing.academicYear(),
                                existing.semester(),
                                existing.marksPercentage(),
                                existing.grade(),
                                as.attendancePercentage()));
            }
        }

        return List.copyOf(byKey.values());
    }

    private String key(Long subjectId, String academicYear, Integer semester) {
        return subjectId + "|" + academicYear + "|" + semester;
    }

    /**
     * The weak-subject rule (§stated once in the Phase 6 task): below-threshold marks
     * OR below-threshold attendance, never a null percentage. Ordered worst-first by
     * marks percentage, nulls last.
     */
    private List<WeakSubject> computeWeakSubjects(List<SubjectRow> rows) {
        List<WeakSubject> result = new ArrayList<>();
        for (SubjectRow row : rows) {
            boolean marksWeak = row.marksPercentage() != null && row.marksPercentage().compareTo(marksThreshold) < 0;
            boolean attendanceWeak =
                    row.attendancePercentage() != null && row.attendancePercentage().compareTo(attendanceThreshold) < 0;
            if (!marksWeak && !attendanceWeak) {
                continue;
            }
            List<String> reasons = new ArrayList<>();
            if (marksWeak) {
                reasons.add("marks " + row.marksPercentage() + "% is below " + marksThreshold + "%");
            }
            if (attendanceWeak) {
                reasons.add("attendance " + row.attendancePercentage() + "% is below " + attendanceThreshold + "%");
            }
            result.add(
                    new WeakSubject(
                            row.subjectId(),
                            row.subjectCode(),
                            row.subjectName(),
                            row.marksPercentage(),
                            row.attendancePercentage(),
                            String.join("; ", reasons)));
        }
        result.sort(Comparator.comparing(WeakSubject::marksPercentage, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private AISubjectPerformanceResponse toSubjectPerformanceResponse(SubjectRow row) {
        boolean lowAttendance =
                row.attendancePercentage() != null && row.attendancePercentage().compareTo(attendanceThreshold) < 0;
        return new AISubjectPerformanceResponse(
                row.subjectId(),
                row.subjectCode(),
                row.subjectName(),
                row.credits(),
                row.academicYear(),
                row.semester(),
                row.marksPercentage(),
                row.grade(),
                row.attendancePercentage(),
                lowAttendance);
    }
}
