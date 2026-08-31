package smartcampus.service;

/**
 * A point-in-time read of one caller's AI rate-limit usage (§61) — never throws, unlike
 * {@link AIRateLimiter#checkAllowed}. {@code remainingMinute}/{@code remainingDay} are
 * floored at zero (a caller who is already over a limit is never shown a negative
 * "remaining" count).
 */
public record AIRateLimitSnapshot(
        int perMinute,
        int perDay,
        long usedLastMinute,
        long usedToday,
        long remainingMinute,
        long remainingDay) {}
