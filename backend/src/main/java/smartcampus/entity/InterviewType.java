package smartcampus.entity;

/**
 * The type of an interview.
 *
 * <p>Stored as the string name (not the ordinal) in {@code interviews.interview_type}.
 */
public enum InterviewType {
    TECHNICAL,
    HR,
    BEHAVIOURAL,
    CODING,
    APTITUDE,
    MANAGERIAL,
    MOCK
}
