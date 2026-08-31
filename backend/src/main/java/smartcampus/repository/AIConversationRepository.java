package smartcampus.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.AIConversation;
import smartcampus.entity.AIFeature;

/**
 * Persistence access for {@link AIConversation}.
 *
 * <p>{@link #findByIdAndUserId} is the ONLY way a conversation is ever loaded for a
 * caller — ownership must never be checked after an unscoped {@code findById}, per the
 * non-probing 404 rule (a miss must read the same as "not found", never leak a 403).
 */
public interface AIConversationRepository extends JpaRepository<AIConversation, Long> {

    Optional<AIConversation> findByIdAndUserId(Long id, Long userId);

    Page<AIConversation> findByUserId(Long userId, Pageable pageable);

    Page<AIConversation> findByUserIdAndFeature(Long userId, AIFeature feature, Pageable pageable);

    Page<AIConversation> findByUserIdAndTitleContainingIgnoreCase(
            Long userId, String title, Pageable pageable);

    Page<AIConversation> findByUserIdAndFeatureAndTitleContainingIgnoreCase(
            Long userId, AIFeature feature, String title, Pageable pageable);
}
