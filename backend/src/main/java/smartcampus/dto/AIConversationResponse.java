package smartcampus.dto;

import java.time.LocalDateTime;
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIFeature;

/** Response representation of an {@link AIConversation}, safe for list and detail screens. */
public record AIConversationResponse(
        Long id,
        String title,
        AIFeature feature,
        String model,
        int messageCount,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** Reads no lazy association — safe to call outside the persistence context. */
    public static AIConversationResponse from(AIConversation c) {
        return new AIConversationResponse(
                c.getId(),
                c.getTitle(),
                c.getFeature(),
                c.getModel(),
                c.getMessageCount(),
                c.getLastMessageAt(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
