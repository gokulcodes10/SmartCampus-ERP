package smartcampus.service;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIFeature;
import smartcampus.entity.AIMessage;
import smartcampus.entity.AIMessageRole;
import smartcampus.entity.User;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.AIConversationRepository;
import smartcampus.repository.AIMessageRepository;

/**
 * All persistence for one AI conversation's lifecycle, in its own bean so every method
 * truly commits independently — the same shape as {@link CodingSubmissionRecorder} for
 * coding submissions.
 *
 * <p><b>THE TRANSACTION SPLIT.</b> {@code AIAssistantService}/{@code AIStudyPlanService}
 * call out to the external AI provider between authorizing a turn and recording it. If
 * that whole sequence lived in one {@code @Transactional} method, a thrown exception
 * from the provider call would roll back anything already saved in the same method —
 * this project's Phase 2 brute-force-counter trap. Because a {@code @Transactional}
 * method invoked on {@code this} bypasses the Spring AOP proxy entirely and would
 * silently not even start a new transaction, the fix isn't a try/catch inside one
 * orchestration method — it's this separate bean, injected into the orchestrators, so
 * each of {@link #createConversation}, {@link #append}, {@link #rename} and
 * {@link #delete} really does commit on its own, called ONLY after a real provider
 * response (or an ownership-verified rename/delete) exists.
 */
@Service
public class AIConversationRecorder {

    private final AIConversationRepository aiConversationRepository;
    private final AIMessageRepository aiMessageRepository;

    public AIConversationRecorder(
            AIConversationRepository aiConversationRepository, AIMessageRepository aiMessageRepository) {
        this.aiConversationRepository = aiConversationRepository;
        this.aiMessageRepository = aiMessageRepository;
    }

    /** Inserts a new conversation row and commits. */
    @Transactional
    public AIConversation createConversation(User owner, String title, AIFeature feature, String model) {
        AIConversation conversation =
                AIConversation.builder().user(owner).title(title).feature(feature).model(model).build();
        return aiConversationRepository.save(conversation);
    }

    /**
     * Appends one turn: assigns {@code seq_no = findMaxSeqNo(conversationId) + 1},
     * inserts the message, then recomputes {@code message_count} and
     * {@code last_message_at} on the parent conversation from the real row count — never
     * an incremented counter that could drift from the table — and commits both writes
     * together. {@code completionOrNull} supplies the token/latency figures for an
     * ASSISTANT turn; {@code null} for SYSTEM/USER turns, which never carry usage data.
     */
    @Transactional
    public AIMessage append(
            Long conversationId,
            AIMessageRole role,
            String content,
            boolean grounded,
            String model,
            AICompletion completionOrNull) {
        AIConversation conversation =
                aiConversationRepository
                        .findById(conversationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));

        int nextSeqNo = aiMessageRepository.findMaxSeqNo(conversationId) + 1;
        AIMessage message =
                AIMessage.builder()
                        .conversation(conversation)
                        .seqNo(nextSeqNo)
                        .role(role)
                        .content(content)
                        .model(model)
                        .grounded(grounded)
                        .promptTokens(completionOrNull != null ? completionOrNull.promptTokens() : null)
                        .completionTokens(completionOrNull != null ? completionOrNull.completionTokens() : null)
                        .totalTokens(completionOrNull != null ? completionOrNull.totalTokens() : null)
                        .latencyMs(completionOrNull != null ? (int) completionOrNull.latencyMs() : null)
                        .build();
        message = aiMessageRepository.saveAndFlush(message);

        long count = aiMessageRepository.countByConversationId(conversationId);
        conversation.setMessageCount((int) count);
        conversation.setLastMessageAt(message.getCreatedAt());
        aiConversationRepository.save(conversation);

        return message;
    }

    /** The content of the conversation's most recent stored SYSTEM message, or {@code null} when none exists. */
    @Transactional(readOnly = true)
    public String lastSystemPrompt(Long conversationId) {
        List<AIMessage> messages = aiMessageRepository.findByConversationIdOrderBySeqNoAsc(conversationId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            AIMessage message = messages.get(i);
            if (message.getRole() == AIMessageRole.SYSTEM) {
                return message.getContent();
            }
        }
        return null;
    }

    /** Renames a conversation. Ownership must already be verified by the caller before invoking this. */
    @Transactional
    public void rename(Long conversationId, String title) {
        AIConversation conversation =
                aiConversationRepository
                        .findById(conversationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
        conversation.setTitle(title);
        aiConversationRepository.save(conversation);
    }

    /**
     * Deletes a conversation and, via the database's {@code ON DELETE CASCADE} on
     * {@code ai_messages.conversation_id}, every message in it. Ownership must already
     * be verified by the caller before invoking this.
     */
    @Transactional
    public void delete(Long conversationId) {
        AIConversation conversation =
                aiConversationRepository
                        .findById(conversationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Conversation not found."));
        aiConversationRepository.delete(conversation);
    }
}
