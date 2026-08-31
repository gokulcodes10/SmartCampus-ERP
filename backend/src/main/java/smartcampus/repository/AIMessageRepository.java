package smartcampus.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.AIMessage;

/**
 * Persistence access for {@link AIMessage}.
 *
 * <p>{@link #findMaxSeqNo} returns {@code -1} for a conversation with no messages yet,
 * so the caller can always assign the next {@code seq_no} as {@code findMaxSeqNo(id) +
 * 1} without a separate empty-conversation branch.
 */
public interface AIMessageRepository extends JpaRepository<AIMessage, Long> {

    List<AIMessage> findByConversationIdOrderBySeqNoAsc(Long conversationId);

    long countByConversationId(Long conversationId);

    @Query("select coalesce(max(m.seqNo), -1) from AIMessage m where m.conversation.id = :conversationId")
    int findMaxSeqNo(@Param("conversationId") Long conversationId);
}
