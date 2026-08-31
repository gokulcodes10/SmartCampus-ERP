package smartcampus.entity;

/**
 * The lifecycle states of an {@link Exam}.
 *
 * <p>Stored as the string name (not the ordinal) in {@code exams.status} — see
 * {@code V4__academic_operations.sql}, which also enforces these exact three values
 * with a {@code CHECK} constraint. A {@link #CANCELLED} exam never contributes to a
 * student's grade (PROJECT_PLAN.md clarification G7) — every grading query excludes it.
 */
public enum ExamStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED
}
