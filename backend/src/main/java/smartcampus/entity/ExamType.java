package smartcampus.entity;

/**
 * The kinds of assessment an {@link Exam} row can represent.
 *
 * <p>Stored as the string name (not the ordinal) in {@code exams.exam_type} — see
 * {@code V4__academic_operations.sql}, which also enforces these exact seven values
 * with a {@code CHECK} constraint.
 *
 * <p>PROJECT_PLAN.md clarification G5: an "assignment" is simply {@code examType =
 * ASSIGNMENT}. There is no separate Assignment entity and no submission module in this
 * build — do not invent one.
 */
public enum ExamType {
    INTERNAL_1,
    INTERNAL_2,
    INTERNAL_3,
    ASSIGNMENT,
    QUIZ,
    MODEL,
    SEMESTER
}
