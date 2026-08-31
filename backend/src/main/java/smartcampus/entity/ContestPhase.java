package smartcampus.entity;

/**
 * The time-derived phase of a coding contest. NEVER PERSISTED - there is deliberately
 * no column for this on {@code coding_contests}; a stored "running" value would go
 * stale the instant the clock passed it and nothing wrote the row, which is exactly the
 * kind of fake state §69 forbids. Compute this fresh every time it is asked, from the
 * contest's {@code startTime}/{@code endTime} against {@code LocalDateTime.now()}:
 *
 * <pre>
 *   now &lt; startTime              -&gt; UPCOMING
 *   startTime &lt;= now &lt;= endTime  -&gt; RUNNING
 *   now &gt; endTime                -&gt; ENDED
 * </pre>
 *
 * <p>A {@link ContestStatus#CANCELLED} contest still reports its time-derived phase;
 * the UI shows the {@link ContestStatus} separately.
 */
public enum ContestPhase {
    UPCOMING,
    RUNNING,
    ENDED
}
