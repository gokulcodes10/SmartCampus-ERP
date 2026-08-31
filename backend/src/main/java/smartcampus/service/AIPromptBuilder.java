package smartcampus.service;

import java.util.List;
import org.springframework.stereotype.Component;
import smartcampus.dto.AIExplainRequest;
import smartcampus.dto.AIMcqRequest;
import smartcampus.dto.AIPracticeQuestionsRequest;
import smartcampus.dto.AIStudyPlanGenerateRequest;
import smartcampus.dto.AttendanceSubjectSummary;
import smartcampus.dto.AttendanceSummaryResponse;
import smartcampus.dto.ExamResponse;
import smartcampus.dto.SemesterGradeSummary;
import smartcampus.dto.SubjectGradeSummary;
import smartcampus.entity.AIStudyPlanType;

/**
 * Renders the grounded prompts sent to the AI provider. PURE: no repository, no clock,
 * no randomness — every method is a deterministic function of its arguments, so the
 * same {@link StudentAcademicContext} (or request) always renders byte-identical text.
 * {@link AIContextService} is the only place that touches the database; this class only
 * formats what it is handed.
 *
 * <p>{@link #systemPrompt} is the anti-fabrication contract itself: it states plainly,
 * section by section, exactly which real figures the model may use and instructs it
 * never to invent a number, date or subject that is not printed above the RULES
 * section — including saying so explicitly when a section has nothing to show, so an
 * empty record can never be pattern-matched into generic-sounding fabricated advice.
 */
@Component
public class AIPromptBuilder {

    /**
     * The full grounding prompt for one student — sent as the SYSTEM message on every
     * conversation turn (§9: rebuilt fresh every turn from live data, and re-persisted
     * only when it differs from the conversation's last stored SYSTEM message).
     */
    public String systemPrompt(StudentAcademicContext ctx) {
        StringBuilder sb = new StringBuilder();

        sb.append("ROLE:\n");
        sb.append(
                "You are an academic study assistant for this specific student at this college. "
                        + "Every figure you use MUST come from the sections below, which are read directly "
                        + "from the college's own records for this student.\n\n");

        sb.append("STUDENT:\n");
        appendLine(sb, "Name", ctx.studentName());
        appendLine(sb, "Register Number", ctx.registerNumber());
        appendLine(sb, "Department", ctx.departmentName());
        appendLine(sb, "Course", ctx.courseName());
        appendLine(sb, "Current Semester", ctx.currentSemester() != null ? String.valueOf(ctx.currentSemester()) : null);
        appendLine(sb, "Section", ctx.section());
        sb.append("\n");

        if (!ctx.hasAcademicData()) {
            sb.append(
                    "OVERALL: this student's record is empty — no grades, no attendance history and no "
                            + "upcoming exams exist in the system yet.\n\n");
        }

        appendAcademicRecord(sb, ctx);
        appendAttendance(sb, ctx);
        appendWeakSubjects(sb, ctx);
        appendUpcomingExams(sb, ctx);
        appendRules(sb);

        return sb.toString();
    }

    private void appendAcademicRecord(StringBuilder sb, StudentAcademicContext ctx) {
        sb.append("ACADEMIC RECORD:\n");
        List<SemesterGradeSummary> semesters = ctx.marks().semesters();
        if (semesters == null || semesters.isEmpty()) {
            sb.append("No marks have been recorded for this student yet.\n\n");
            return;
        }
        for (SemesterGradeSummary semester : semesters) {
            sb.append("Academic Year ")
                    .append(semester.academicYear())
                    .append(", Semester ")
                    .append(semester.semester())
                    .append(":\n");
            if (semester.subjects().isEmpty()) {
                sb.append("  (no subjects recorded this semester)\n");
            }
            for (SubjectGradeSummary sg : semester.subjects()) {
                sb.append("  - ")
                        .append(sg.subjectCode())
                        .append(" ")
                        .append(sg.subjectName())
                        .append(" (credits ")
                        .append(sg.credits())
                        .append("): ");
                if (sg.percentage() == null) {
                    sb.append("not graded yet");
                } else {
                    sb.append(sg.totalObtained())
                            .append("/")
                            .append(sg.totalMaximum())
                            .append(" = ")
                            .append(sg.percentage())
                            .append("% (grade ")
                            .append(sg.grade() != null ? sg.grade() : "not available")
                            .append(")");
                }
                sb.append("\n");
            }
        }
        sb.append("Total graded credits: ").append(ctx.marks().totalGradedCredits()).append("\n");
        sb.append("CGPA: ")
                .append(ctx.marks().cgpa() != null ? ctx.marks().cgpa().toString() : "not available yet")
                .append("\n\n");
    }

    private void appendAttendance(StringBuilder sb, StudentAcademicContext ctx) {
        sb.append("ATTENDANCE:\n");
        AttendanceSummaryResponse attendance = ctx.attendance();
        List<AttendanceSubjectSummary> subjects = attendance.subjects();
        if (subjects == null || subjects.isEmpty()) {
            sb.append("No attendance records exist for this student yet.\n\n");
            return;
        }
        sb.append("Overall attendance: ")
                .append(attendance.overallPercentage() != null ? attendance.overallPercentage() + "%" : "not available")
                .append("\n");
        sb.append("Minimum required attendance: ").append(ctx.minimumAttendancePercentage()).append("%\n");
        for (AttendanceSubjectSummary as : subjects) {
            sb.append("  - ")
                    .append(as.subjectCode())
                    .append(" ")
                    .append(as.subjectName())
                    .append(" (Academic Year ")
                    .append(as.academicYear())
                    .append(", Semester ")
                    .append(as.semester())
                    .append("): ");
            if (as.heldClasses() == 0) {
                sb.append("no classes held yet");
            } else {
                sb.append(as.attendedClasses())
                        .append("/")
                        .append(as.heldClasses())
                        .append(" classes attended = ")
                        .append(as.attendancePercentage())
                        .append("%");
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void appendWeakSubjects(StringBuilder sb, StudentAcademicContext ctx) {
        sb.append("WEAK SUBJECTS:\n");
        List<WeakSubject> weakSubjects = ctx.weakSubjects();
        if (weakSubjects == null || weakSubjects.isEmpty()) {
            sb.append("None identified from the available records.\n\n");
            return;
        }
        for (WeakSubject w : weakSubjects) {
            sb.append("  - ").append(w.subjectCode()).append(" ").append(w.subjectName()).append(": ").append(w.reason()).append("\n");
        }
        sb.append("\n");
    }

    private void appendUpcomingExams(StringBuilder sb, StudentAcademicContext ctx) {
        sb.append("UPCOMING EXAMS:\n");
        List<ExamResponse> upcoming = ctx.upcomingExams();
        if (upcoming == null || upcoming.isEmpty()) {
            sb.append("None scheduled.\n\n");
            return;
        }
        for (ExamResponse exam : upcoming) {
            sb.append("  - ")
                    .append(exam.examDate())
                    .append(" ")
                    .append(exam.subjectCode())
                    .append(" ")
                    .append(exam.subjectName())
                    .append(", ")
                    .append(exam.examType())
                    .append(", \"")
                    .append(exam.title())
                    .append("\", maximum marks ")
                    .append(exam.maximumMarks())
                    .append("\n");
        }
        sb.append("\n");
    }

    private void appendRules(StringBuilder sb) {
        sb.append("RULES FOR THE ASSISTANT:\n");
        sb.append("  - Use only the figures given in the sections above; treat nothing else as known.\n");
        sb.append(
                "  - Never invent marks, attendance, exam dates, subjects or grades that are not printed above.\n");
        sb.append(
                "  - If the student asks about something the record above does not contain, say plainly that "
                        + "it is not in the record instead of guessing.\n");
        sb.append(
                "  - When a section above is empty, say the record is empty for that section rather than "
                        + "giving generic advice dressed up as personalised advice.\n");
        sb.append("  - Keep all advice advisory: the student makes their own decisions.\n");
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sb.append(label).append(": ").append(value).append("\n");
    }

    // ------------------------------------------------------------------
    // Feature instructions
    // ------------------------------------------------------------------

    /**
     * Instructs the model to return the JSON study-plan/revision-schedule shape
     * defined by the Phase 6 contract §10 — parsed by {@code AIStudyPlanService}, never
     * by this class.
     */
    public String studyPlanInstruction(
            StudentAcademicContext ctx, AIStudyPlanGenerateRequest req, AIStudyPlanType type) {
        StringBuilder sb = new StringBuilder();
        boolean revision = type == AIStudyPlanType.REVISION_SCHEDULE;

        sb.append(revision ? "Build a revision schedule" : "Build a study plan");
        sb.append(" for this student covering ").append(req.startDate()).append(" to ").append(req.endDate()).append(".\n");
        if (req.title() != null && !req.title().isBlank()) {
            sb.append("Requested title: ").append(req.title()).append("\n");
        }
        if (req.goal() != null && !req.goal().isBlank()) {
            sb.append("Requested goal: ").append(req.goal()).append("\n");
        }
        if (req.dailyMinutes() != null) {
            sb.append("Target about ").append(req.dailyMinutes()).append(" minutes of study per day.\n");
        }
        if (req.subjectIds() != null && !req.subjectIds().isEmpty()) {
            sb.append("Focus only on subject IDs ")
                    .append(req.subjectIds())
                    .append(" — match these against the subject codes listed in the ACADEMIC RECORD and "
                            + "ATTENDANCE sections above.\n");
        }
        if (revision) {
            sb.append(
                    "This is REVISION for material already covered, prioritising the WEAK SUBJECTS listed "
                            + "above and any subject with an upcoming exam.\n");
        } else if (!ctx.weakSubjects().isEmpty()) {
            sb.append("Give extra time to the WEAK SUBJECTS listed above.\n");
        }
        sb.append(
                "Every item's scheduledDate MUST fall within the requested date range. Base every item only "
                        + "on the real subjects, exams and performance data given above — never invent a "
                        + "subject, exam or number.\n");
        sb.append("Respond with ONLY a single JSON object of this exact shape, no prose outside it:\n");
        sb.append(
                "{\"title\":\"...\",\"goal\":\"...\",\"items\":[{\"scheduledDate\":\"YYYY-MM-DD\","
                        + "\"subjectCode\":\"CS201\"|null,\"subjectLabel\":\"...\",\"title\":\"...\","
                        + "\"description\":\"...\",\"durationMinutes\":60}]}\n");
        return sb.toString();
    }

    /** Instructs the model to explain one topic, grounded in the student's own program/level. */
    public String explainInstruction(AIExplainRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Explain the topic \"").append(req.topic()).append("\" clearly for this student, ")
                .append("pitched at the level implied by their program and current semester above.\n");
        if (req.focus() != null && !req.focus().isBlank()) {
            sb.append("Focus specifically on: ").append(req.focus()).append("\n");
        }
        if (req.subjectId() != null) {
            sb.append("This relates to subject ID ")
                    .append(req.subjectId())
                    .append(" — if that subject is listed above, connect the explanation to its syllabus.\n");
        }
        sb.append("Use plain language with a worked example where useful. Do not fabricate facts outside the topic.\n");
        return sb.toString();
    }

    /** Instructs the model to generate free-form practice questions on one topic. */
    public String practiceQuestionsInstruction(AIPracticeQuestionsRequest req) {
        int count = req.count() != null ? req.count() : 5;
        StringBuilder sb = new StringBuilder();
        sb.append("Generate ").append(count).append(" practice questions on \"").append(req.topic()).append("\"");
        sb.append(req.difficulty() != null ? " at " + req.difficulty() + " difficulty" : " covering a mix of difficulties");
        sb.append(".\n");
        if (req.subjectId() != null) {
            sb.append("This relates to subject ID ").append(req.subjectId()).append(" if it is listed above.\n");
        }
        sb.append("Number each question. Include a brief model answer or approach after each question.\n");
        return sb.toString();
    }

    /** Instructs the model to generate multiple-choice questions with a marked correct answer. */
    public String mcqInstruction(AIMcqRequest req) {
        int count = req.count() != null ? req.count() : 5;
        StringBuilder sb = new StringBuilder();
        sb.append("Generate ").append(count).append(" multiple-choice questions on \"").append(req.topic()).append("\"");
        sb.append(req.difficulty() != null ? " at " + req.difficulty() + " difficulty" : " covering a mix of difficulties");
        sb.append(".\n");
        if (req.subjectId() != null) {
            sb.append("This relates to subject ID ").append(req.subjectId()).append(" if it is listed above.\n");
        }
        sb.append(
                "Each question needs exactly 4 labelled options (A-D) with exactly one correct answer. "
                        + "Number each question, then state the correct option letter and a one-line reason.\n");
        return sb.toString();
    }
}
