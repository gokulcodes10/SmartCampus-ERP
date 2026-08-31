package smartcampus.entity;

/**
 * The status of an interview in its lifecycle.
 *
 * <p>Stored as the string name (not the ordinal) in {@code interviews.status}.
 * SCHEDULED and RESCHEDULED interviews are "live" and block scheduling; COMPLETED,
 * CANCELLED and NO_SHOW are terminal.
 */
public enum InterviewStatus {
    SCHEDULED,
    RESCHEDULED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
