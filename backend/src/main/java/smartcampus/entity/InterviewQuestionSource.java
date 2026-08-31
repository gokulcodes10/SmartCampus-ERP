package smartcampus.entity;

/**
 * The source of an interview question.
 *
 * <p>Stored as the string name (not the ordinal) in
 * {@code interview_questions.source}. CURATED questions are authored by an admin and
 * visible to all; AI_GENERATED questions are produced by the AI service and belong to
 * a single student.
 */
public enum InterviewQuestionSource {
    CURATED,
    AI_GENERATED
}
