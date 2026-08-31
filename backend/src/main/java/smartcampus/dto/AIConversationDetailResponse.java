package smartcampus.dto;

import java.util.List;

/** A conversation plus its full message history in {@code seq_no} order. Assembled by the service. */
public record AIConversationDetailResponse(
        AIConversationResponse conversation, List<AIMessageResponse> messages) {}
