package smartcampus.service;

import smartcampus.entity.AIMessageRole;

/**
 * One turn in a chat-completion request, as sent to the AI provider. {@code role} maps
 * to the provider's lowercase role string ({@code SYSTEM -> "system"} etc.) inside
 * {@link GroqAIService}; nothing here is persisted directly - see
 * {@code AIConversationRecorder} in the orchestration layer for that.
 */
public record AIChatMessage(AIMessageRole role, String content) {}
