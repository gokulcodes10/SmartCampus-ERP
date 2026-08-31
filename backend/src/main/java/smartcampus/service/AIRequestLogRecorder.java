package smartcampus.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIFeature;
import smartcampus.entity.AIRequestLog;
import smartcampus.entity.AIRequestOutcome;
import smartcampus.entity.User;
import smartcampus.repository.AIRequestLogRepository;

/**
 * Writes the §61/§69 ledger row for one AI request attempt, in its OWN bean with
 * {@code @Transactional(propagation = REQUIRES_NEW)} — this project's Phase 2
 * brute-force-counter trap, in a new shape.
 *
 * <p>The orchestrating service (e.g. {@code AIAssistantService}) calls the provider
 * outside any open transaction and, on failure, throws {@code AIUnavailableException}.
 * If the ledger row were written inside that same caller's transaction, Spring's
 * default rollback-on-unchecked rule would roll back the very row that proves the
 * attempt happened — the row the rate limiter depends on. A {@code @Transactional}
 * method invoked on {@code this} bypasses the Spring AOP proxy entirely and starts no
 * new transaction at all, so the fix isn't a try/catch inside one method — it's this
 * separate bean, injected into the orchestrator, so {@link #record} really does commit
 * on its own regardless of what the caller does next. See {@link
 * CodingSubmissionRecorder}, which solves the identical problem for coding submissions.
 */
@Service
public class AIRequestLogRecorder {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;
    private static final String DEFAULT_ERROR_MESSAGE = "Unknown error.";

    private final AIRequestLogRepository aiRequestLogRepository;

    public AIRequestLogRecorder(AIRequestLogRepository aiRequestLogRepository) {
        this.aiRequestLogRepository = aiRequestLogRepository;
    }

    /**
     * Persists one attempt and commits immediately, mirroring the {@code
     * ai_request_logs} CHECK constraints in Java so a violation never reaches MySQL:
     * {@code SUCCESS} requires a non-null {@code model} and forces {@code
     * errorMessage} to null; any other outcome requires a non-blank {@code
     * errorMessage} (substituting "Unknown error." when the caller passed none),
     * truncated to 500 characters. Token counts and latency come from {@code
     * completion} when present, {@code null} otherwise. {@code errorMessage} must
     * NEVER contain the API key, an Authorization header, or prompt content — the
     * caller is responsible for passing a message that does not.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            User user,
            AIConversation conversation,
            AIFeature feature,
            AIRequestOutcome outcome,
            String model,
            AICompletion completion,
            String errorMessage) {

        String resolvedError;
        if (outcome == AIRequestOutcome.SUCCESS) {
            if (model == null || model.isBlank()) {
                throw new IllegalStateException(
                        "A SUCCESS AI request log requires a non-null, non-blank model.");
            }
            resolvedError = null;
        } else {
            String candidate = (errorMessage == null || errorMessage.isBlank()) ? DEFAULT_ERROR_MESSAGE : errorMessage;
            resolvedError =
                    candidate.length() > ERROR_MESSAGE_MAX_LENGTH
                            ? candidate.substring(0, ERROR_MESSAGE_MAX_LENGTH)
                            : candidate;
        }

        AIRequestLog log =
                AIRequestLog.builder()
                        .user(user)
                        .conversation(conversation)
                        .feature(feature)
                        .outcome(outcome)
                        .model(model)
                        .promptTokens(completion != null ? completion.promptTokens() : null)
                        .completionTokens(completion != null ? completion.completionTokens() : null)
                        .totalTokens(completion != null ? completion.totalTokens() : null)
                        .latencyMs(completion != null ? (int) completion.latencyMs() : null)
                        .errorMessage(resolvedError)
                        .build();

        aiRequestLogRepository.save(log);
    }
}
