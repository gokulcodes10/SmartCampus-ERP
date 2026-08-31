package smartcampus.dto;

/**
 * One completed exchange: the user's message and the model's answer, both already
 * persisted. Returned by every conversational endpoint (continue, explain, practice
 * questions, MCQs). Assembled by the service.
 */
public record AIChatTurnResponse(
        Long conversationId, AIMessageResponse userMessage, AIMessageResponse assistantMessage) {}
