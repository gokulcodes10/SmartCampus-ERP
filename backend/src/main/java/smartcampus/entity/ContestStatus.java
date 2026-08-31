package smartcampus.entity;

/**
 * The AUTHORING lifecycle of a coding contest.
 *
 * <p>Stored as the string name (not the ordinal) in {@code coding_contests.status}.
 * This is deliberately not "upcoming / running / ended" - that is a function of time,
 * never a stored value, and is computed by {@link ContestPhase} instead.
 *
 * <p><b>DRAFT:</b> Not visible to students; admin only.
 *
 * <p><b>PUBLISHED:</b> Visible and joinable, subject to its time window.
 *
 * <p><b>CANCELLED:</b> Withdrawn by an admin; admin only. Still reports a time-derived
 * {@link ContestPhase} - the UI shows this status separately.
 */
public enum ContestStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}
