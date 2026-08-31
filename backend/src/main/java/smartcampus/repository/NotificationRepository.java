package smartcampus.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.Notification;
import smartcampus.entity.NotificationType;
import smartcampus.repository.projection.AnnouncementRecipientCount;

/**
 * Persistence access for {@link Notification}.
 *
 * <p><strong>CRITICAL SECURITY RULE:</strong> Every query is scoped by
 * {@code user_id} in the WHERE clause. There is no "load by id then compare in
 * Java" path anywhere. Ownership is the whole security model.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNull(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndType(
            Long userId, NotificationType type, Pageable pageable);

    Page<Notification> findByUserIdAndTypeAndReadAtIsNull(
            Long userId, NotificationType type, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update Notification n set n.readAt = :now where n.id = :id and n.user.id = :userId and n.readAt is null")
    int markRead(@Param("id") Long id, @Param("userId") Long userId,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.id = :id and n.user.id = :userId")
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.user.id = :userId")
    int deleteAllForUser(@Param("userId") Long userId);

    @Query(
            "select n.announcement.id as announcementId, count(n) as recipientCount "
                    + "from Notification n where n.announcement.id in :announcementIds "
                    + "group by n.announcement.id")
    List<AnnouncementRecipientCount> countRecipientsByAnnouncementIds(
            @Param("announcementIds") Collection<Long> announcementIds);
}
