package smartcampus.entity;

/**
 * The category of an interview question.
 *
 * <p>Stored as the string name (not the ordinal) in
 * {@code interview_questions.category}. Six categories are defined by §38 of the spec,
 * and the database CHECK constraint enforces them exactly.
 */
public enum InterviewQuestionCategory {
    TECHNICAL,
    HR,
    BEHAVIOURAL,
    CODING,
    APTITUDE,
    COMPANY_SPECIFIC
}
