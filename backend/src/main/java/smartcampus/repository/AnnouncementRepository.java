package smartcampus.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.Announcement;
import smartcampus.entity.AnnouncementAudience;

/**
 * Persistence access for {@link Announcement}.
 *
 * <p>The {@link #findVisible} and {@link #findVisibleById} queries enforce
 * visibility rules for the board: an announcement is ACTIVE if it is published
 * and not expired, and visible to a caller only if they belong to the target
 * audience or department.
 */
public interface AnnouncementRepository
        extends JpaRepository<Announcement, Long>,
                JpaSpecificationExecutor<Announcement> {

    @Query(
            """
            select a from Announcement a
            where a.publishedAt <= :now
              and (a.expiresAt is null or a.expiresAt > :now)
              and (a.audience in :audiences
                   or (:departmentId is not null and a.department.id = :departmentId))
            """)
    Page<Announcement> findVisible(
            @Param("audiences") Collection<AnnouncementAudience> audiences,
            @Param("departmentId") Long departmentId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query(
            """
            select a from Announcement a
            where a.id = :id
              and a.publishedAt <= :now
              and (a.expiresAt is null or a.expiresAt > :now)
              and (a.audience in :audiences
                   or (:departmentId is not null and a.department.id = :departmentId))
            """)
    Optional<Announcement> findVisibleById(
            @Param("id") Long id,
            @Param("audiences") Collection<AnnouncementAudience> audiences,
            @Param("departmentId") Long departmentId,
            @Param("now") LocalDateTime now);
}
