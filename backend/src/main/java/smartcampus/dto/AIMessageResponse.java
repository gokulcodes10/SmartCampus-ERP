package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.AIMessage;
import smartcampus.entity.AIMessageRole;

/** Response representation of one {@link AIMessage} turn. */
public record AIMessageResponse(
        Long id,
        Long conversationId,
        int seqNo,
        AIMessageRole role,
        String content,
        String model,
        boolean grounded,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer latencyMs,
        LocalDateTime createdAt) {

    /**
     * Reads the lazy {@code conversation} association for its id only, so the caller
     * must still be inside the persistence context that loaded {@code m} (i.e. call
     * this from within the {@code @Transactional} service method, not after it
     * returns).
     */
    public static AIMessageResponse from(AIMessage m) {
        return new AIMessageResponse(
                m.getId(),
                m.getConversation().getId(),
                m.getSeqNo(),
                m.getRole(),
                m.getContent(),
                m.getModel(),
                m.isGrounded(),
                m.getPromptTokens(),
                m.getCompletionTokens(),
                m.getTotalTokens(),
                m.getLatencyMs(),
                m.getCreatedAt());
    }
}
