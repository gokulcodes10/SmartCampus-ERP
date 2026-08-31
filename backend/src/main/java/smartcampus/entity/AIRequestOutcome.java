package smartcampus.entity;

/**
 * What actually happened for one attempted AI request, recorded in {@link AIRequestLog}
 * whether the provider answered or not (§69 — a failure is recorded as a failure, never
 * a fabricated answer).
 *
 * <p>Stored as the string name (not the ordinal) in {@code ai_request_logs.outcome} —
 * see {@code V6__ai.sql}, which also enforces these exact four values with a {@code
 * CHECK} constraint. {@link #SUCCESS} requires a non-null {@code model} and a null
 * {@code error_message}; every other outcome requires the opposite
 * ({@code chk_ai_request_logs_error_matches_outcome}).
 */
public enum AIRequestOutcome {
    SUCCESS,
    PROVIDER_ERROR,
    NOT_CONFIGURED,
    INVALID_RESPONSE
}
