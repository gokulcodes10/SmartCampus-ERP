package smartcampus.entity;

/**
 * The outcome of a completed interview.
 *
 * <p>Stored as the string name (not the ordinal) in {@code interviews.outcome}.
 * May only be non-null when {@code status = COMPLETED}.
 */
public enum InterviewOutcome {
    AWAITING_RESULT,
    SELECTED,
    REJECTED,
    ON_HOLD
}
