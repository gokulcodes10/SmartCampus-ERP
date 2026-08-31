package smartcampus.service;

import java.util.List;
import smartcampus.exception.AIUnavailableException;

/**
 * The §69/§70 abstraction over an external AI chat-completion provider. The application
 * server never fabricates an AI response itself; every implementation of this interface
 * delegates to a real provider (currently Groq's OpenAI-compatible API, see
 * {@link GroqAIService}) reachable at a configured base URL and API key.
 *
 * <p>There is no fallback, no canned answer, and no cached previous reply: when a real
 * answer cannot be obtained, every method throws {@link AIUnavailableException} instead
 * of answering at all. A question with no real answer must never appear to have one.
 */
public interface AIService {

    /**
     * Whether this implementation has enough configuration to be attempted at all
     * (a non-blank API key AND a non-blank base URL). Does not guarantee the provider
     * is reachable or that the configured model (if any) still exists.
     */
    boolean isConfigured();

    /**
     * Resolves the model id this service will actually send on the next
     * {@link #complete} call. The id is never guessed: it is confirmed against the
     * provider's live model list (subject to a short cache of a SUCCESSFUL resolution
     * only - a failure is never cached). See {@link GroqAIService} for the exact
     * resolution algorithm.
     *
     * @throws AIUnavailableException when not configured, when a configured model id is
     *         not present in the live list, or when no candidate preference is available
     *         at the provider
     */
    String resolveModel();

    /**
     * The live {@code GET {baseUrl}/models} listing.
     *
     * @throws AIUnavailableException when not configured or the provider could not be
     *         reached / parsed
     */
    List<AIModelInfo> listModels();

    /**
     * Runs one chat-completion request against the provider and returns the real
     * result. Never returns a partial or fabricated completion.
     *
     * @throws AIUnavailableException when a real completion could not be obtained
     *         (not configured, unreachable, non-2xx response, unparseable body, or an
     *         empty/blank response)
     */
    AICompletion complete(AICompletionRequest request);
}
