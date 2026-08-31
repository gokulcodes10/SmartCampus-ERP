package smartcampus.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import smartcampus.dto.AcademicResultResponse;
import smartcampus.dto.AttendanceSubjectSummary;
import smartcampus.dto.AttendanceSummaryResponse;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.SemesterGradeSummary;
import smartcampus.dto.SubjectGradeSummary;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;
import smartcampus.service.AIPromptBuilder;
import smartcampus.service.StudentAcademicContext;
import smartcampus.service.WeakSubject;

/**
 * Plain JUnit tests for {@link AIPromptBuilder#systemPrompt} — no Spring context, no
 * database. {@link StudentAcademicContext} instances are built by hand so every
 * assertion below traces to a literal value this test wrote itself, matching the
 * anti-fabrication discipline the prompt itself is meant to enforce (§69).
 */
class AIPromptBuilderTest {

    private final AIPromptBuilder builder = new AIPromptBuilder();

    private static AttendanceSummaryResponse emptyAttendance() {
        return new AttendanceSummaryResponse(
                1L, "REG001", "Test Student", null, null, 0, 0, 0, 0, null, new BigDecimal("75"), false, List.of());
    }

    private static AcademicResultResponse emptyMarks() {
        return new AcademicResultResponse(1L, "REG001", "Test Student", 0, null, List.of());
    }

    @Test
    void realMarksAttendanceAndExamDateAppearLiterallyInThePrompt() {
        SubjectGradeSummary subject =
                new SubjectGradeSummary(
                        10L,
                        "CS201",
                        "Data Structures",
                        4,
                        "2025-26",
                        3,
                        2,
                        new BigDecimal("82.50"),
                        new BigDecimal("100.00"),
                        new BigDecimal("82.50"),
                        "A",
                        new BigDecimal("9.0"),
                        true);
        SemesterGradeSummary semester =
                new SemesterGradeSummary("2025-26", 3, 1, 4, new BigDecimal("9.0"), List.of(subject));
        AcademicResultResponse marks =
                new AcademicResultResponse(1L, "REG001", "Test Student", 4, new BigDecimal("9.0"), List.of(semester));

        AttendanceSubjectSummary attendanceSubject =
                new AttendanceSubjectSummary(10L, "CS201", "Data Structures", 4, "2025-26", 3, 20, 20, 17, 0, new BigDecimal("85.00"), false);
        AttendanceSummaryResponse attendance =
                new AttendanceSummaryResponse(
                        1L, "REG001", "Test Student", null, null, 20, 20, 17, 0, new BigDecimal("85.00"), new BigDecimal("75"), false, List.of(attendanceSubject));

        LocalDate examDate = LocalDate.of(2026, 9, 15);
        ExamResponse exam =
                new ExamResponse(
                        99L, 10L, "CS201", "Data Structures", 4, 5L, "Dr. Faculty", "Mid Semester Test",
                        ExamType.INTERNAL_1, "2025-26", 3, "A", examDate, new BigDecimal("50"), ExamStatus.SCHEDULED,
                        LocalDateTime.now(), LocalDateTime.now());

        StudentAcademicContext ctx =
                new StudentAcademicContext(
                        1L, "REG001", "Test Student", "Computer Science", "B.Tech CSE", 3, "A",
                        marks, attendance, List.of(), List.of(exam), new BigDecimal("75"), true);

        String prompt = builder.systemPrompt(ctx);

        assertThat(prompt).contains("82.50");
        assertThat(prompt).contains("85.00");
        assertThat(prompt).contains(examDate.toString());
        assertThat(prompt).contains("CS201");
        assertThat(prompt).contains("Test Student");
        assertThat(prompt).contains("REG001");
    }

    @Test
    void nullPercentageRendersAsNotGradedYetAndNeverAsZero() {
        SubjectGradeSummary ungraded =
                new SubjectGradeSummary(11L, "CS301", "Operating Systems", 3, "2025-26", 3, 0, null, null, null, null, null, null);
        SemesterGradeSummary semester = new SemesterGradeSummary("2025-26", 3, 1, 0, null, List.of(ungraded));
        AcademicResultResponse marks = new AcademicResultResponse(1L, "REG001", "Test Student", 0, null, List.of(semester));

        StudentAcademicContext ctx =
                new StudentAcademicContext(
                        1L, "REG001", "Test Student", "Computer Science", "B.Tech CSE", 3, "A",
                        marks, emptyAttendance(), List.of(), List.of(), new BigDecimal("75"), true);

        String prompt = builder.systemPrompt(ctx);

        assertThat(prompt).contains("not graded yet");
        assertThat(prompt).doesNotContain("0.0");
    }

    @Test
    void emptyRecordStatesPlainlyThatItIsEmpty() {
        StudentAcademicContext ctx =
                new StudentAcademicContext(
                        1L, "REG001", "Test Student", "Computer Science", "B.Tech CSE", 1, "A",
                        emptyMarks(), emptyAttendance(), List.of(), List.of(), new BigDecimal("75"), false);

        String prompt = builder.systemPrompt(ctx);

        assertThat(prompt).contains("record is empty");
        assertThat(prompt).contains("No marks have been recorded for this student yet.");
        assertThat(prompt).contains("No attendance records exist for this student yet.");
        assertThat(prompt).contains("None identified from the available records.");
        assertThat(prompt).contains("None scheduled.");
    }

    @Test
    void antiFabricationRulesArePresent() {
        StudentAcademicContext ctx =
                new StudentAcademicContext(
                        1L, "REG001", "Test Student", "Computer Science", "B.Tech CSE", 1, "A",
                        emptyMarks(), emptyAttendance(), List.of(), List.of(), new BigDecimal("75"), false);

        String prompt = builder.systemPrompt(ctx);

        assertThat(prompt).containsIgnoringCase("never invent");
        assertThat(prompt).containsIgnoringCase("advisory");
    }

    @Test
    void weakSubjectReasonsAppearVerbatim() {
        WeakSubject weak =
                new WeakSubject(10L, "CS201", "Data Structures", new BigDecimal("41.5"), new BigDecimal("62.0"), "marks 41.5% is below 50%; attendance 62.0% is below 75%");

        StudentAcademicContext ctx =
                new StudentAcademicContext(
                        1L, "REG001", "Test Student", "Computer Science", "B.Tech CSE", 3, "A",
                        emptyMarks(), emptyAttendance(), List.of(weak), List.of(), new BigDecimal("75"), true);

        String prompt = builder.systemPrompt(ctx);

        assertThat(prompt).contains("marks 41.5% is below 50%; attendance 62.0% is below 75%");
    }

    @Test
    void outputIsByteIdenticalAcrossTwoCallsWithTheSameInput() {
        SubjectGradeSummary subject =
                new SubjectGradeSummary(10L, "CS201", "Data Structures", 4, "2025-26", 3, 2, new BigDecimal("82.50"), new BigDecimal("100.00"), new BigDecimal("82.50"), "A", new BigDecimal("9.0"), true);
        SemesterGradeSummary semester = new SemesterGradeSummary("2025-26", 3, 1, 4, new BigDecimal("9.0"), List.of(subject));
        AcademicResultResponse marks = new AcademicResultResponse(1L, "REG001", "Test Student", 4, new BigDecimal("9.0"), List.of(semester));

        StudentAcademicContext ctx =
                new StudentAcademicContext(
                        1L, "REG001", "Test Student", "Computer Science", "B.Tech CSE", 3, "A",
                        marks, emptyAttendance(), List.of(), List.of(), new BigDecimal("75"), true);

        String first = builder.systemPrompt(ctx);
        String second = builder.systemPrompt(ctx);

        assertThat(first).isEqualTo(second);
    }
}
