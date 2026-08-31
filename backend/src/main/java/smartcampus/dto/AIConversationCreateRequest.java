package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import smartcampus.entity.AIFeature;

/**
 * Request body for {@code POST /api/ai/conversations}: opens a new conversation with
 * its first user message. {@code title} is optional — when blank/absent the service
 * derives one from the first 60 characters of {@code message} (§9 of the phase
 * contract). {@code feature} may be null; the service defaults it to {@link
 * AIFeature#CHAT}.
 */
public record AIConversationCreateRequest(
        @Size(max = 150) String title,
        AIFeature feature,
        @NotBlank @Size(max = 4000) String message) {}
