package smartcampus.service;

import java.util.List;

/**
 * A single chat-completion request to {@link AIService#complete}. {@code temperature}
 * and {@code maxTokens} are optional overrides - when {@code null} the implementation
 * falls back to its configured defaults. {@code jsonObject} requests the provider's
 * JSON-object response mode (used only by AI study-plan generation, per §10 of the
 * phase contract).
 */
public record AICompletionRequest(List<AIChatMessage> messages, Double temperature, Integer maxTokens, boolean jsonObject) {

    public static AICompletionRequest text(List<AIChatMessage> messages) {
        return new AICompletionRequest(messages, null, null, false);
    }

    public static AICompletionRequest json(List<AIChatMessage> messages) {
        return new AICompletionRequest(messages, null, null, true);
    }
}
