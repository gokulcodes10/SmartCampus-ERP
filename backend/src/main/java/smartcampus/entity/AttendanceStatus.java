package smartcampus.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The states one {@link Attendance} row can carry, and — deliberately, so the rule
 * exists in exactly ONE place in the codebase — the attendance-percentage rule itself
 * (PROJECT_PLAN.md clarification G6).
 *
 * <p>Stored as the string name (not the ordinal) in {@code attendance.status} — see
 * {@code V4__academic_operations.sql}, which also enforces these exact five values with
 * a {@code CHECK} constraint.
 *
 * <p>Every status is either "held" (it counts toward the percentage denominator) or not,
 * and either "attended" (it counts toward the numerator) or not. {@link #CANCELLED} is
 * held = false, attended = false: a cancelled class did not happen, so it is excluded
 * from both. No service anywhere in the application may re-derive this table from
 * scratch — call {@link #isHeld()} / {@link #isAttended()} on the status, or use
 * {@link #heldStatuses()} / {@link #attendedStatuses()} to build a query filter.
 */
public enum AttendanceStatus {
    PRESENT(true, true),
    ABSENT(true, false),
    LATE(true, true),
    ON_DUTY(true, true),
    CANCELLED(false, false);

    private final boolean held;
    private final boolean attended;

    AttendanceStatus(boolean held, boolean attended) {
        this.held = held;
        this.attended = attended;
    }

    /** Whether this status counts toward the attendance-percentage DENOMINATOR. */
    public boolean isHeld() {
        return held;
    }

    /** Whether this status counts toward the attendance-percentage NUMERATOR. */
    public boolean isAttended() {
        return attended;
    }

    public static Set<AttendanceStatus> heldStatuses() {
        return EnumSet.of(PRESENT, ABSENT, LATE, ON_DUTY);
    }

    public static Set<AttendanceStatus> attendedStatuses() {
        return EnumSet.of(PRESENT, LATE, ON_DUTY);
    }
}
