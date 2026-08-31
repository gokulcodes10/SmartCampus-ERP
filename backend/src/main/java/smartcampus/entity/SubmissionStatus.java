package smartcampus.entity;

/**
 * The full §29 verdict vocabulary for one coding submission or one per-test-case result.
 *
 * <p>Stored as the string name (not the ordinal) in {@code coding_submissions.status}
 * (all nine values allowed) and in {@code submission_test_results.status} (only the
 * seven terminal values below are allowed there - the database CHECK rejects PENDING
 * and RUNNING on that table, since a per-test-case row is only ever written once the
 * judge has finished with that case).
 *
 * <p>{@link #PENDING} and {@link #RUNNING} are transient states while the judge works.
 * {@link #INTERNAL_ERROR} is the honest outcome when the execution backend cannot be
 * reached or does not return a verdict (clarification G10) - it is never faked as
 * {@link #ACCEPTED}; the database's {@code chk_coding_submissions_accepted_is_earned}
 * constraint makes that impossible even if application code tried.
 */
public enum SubmissionStatus {
    PENDING,
    RUNNING,
    ACCEPTED,
    WRONG_ANSWER,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    COMPILATION_ERROR,
    RUNTIME_ERROR,
    INTERNAL_ERROR
}
