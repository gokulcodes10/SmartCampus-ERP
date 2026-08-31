package smartcampus.dto;

/**
 * The AI subsystem's current configuration and this caller's rate-limit usage, returned
 * by {@code GET /api/ai/status}. Assembled by the service.
 *
 * <p>{@code model} is the configured-or-already-resolved model id, or null — NEVER any
 * part of the provider API key (§25, §61).
 */
public record AIStatusResponse(
        boolean configured,
        String provider,
        String baseUrl,
        String model,
        int rateLimitPerMinute,
        int rateLimitPerDay,
        long usedLastMinute,
        long usedToday,
        long remainingMinute,
        long remainingDay) {}
