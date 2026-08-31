package smartcampus.service;

/**
 * The real, provider-produced result of one {@link AIService#complete} call.
 * {@code promptTokens}/{@code completionTokens}/{@code totalTokens} are {@code null}
 * when the provider did not report usage - never {@code 0} (§69: absence of data is
 * never represented as a zero value). {@code latencyMs} is wall-clock time measured
 * around the HTTP call, not a provider-reported figure.
 */
public record AICompletion(
        String content, String model, Integer promptTokens, Integer completionTokens, Integer totalTokens, long latencyMs) {}
