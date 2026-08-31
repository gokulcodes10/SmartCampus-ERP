package smartcampus.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.ResumeExperience;

/**
 * Persistence access for {@link ResumeExperience}.
 *
 * <p>Supports reading experience entries in display order and bulk deletion by resume.
 */
public interface ResumeExperienceRepository extends JpaRepository<ResumeExperience, Long> {

    /**
     * Finds all experience entries for a resume, ordered for rendering.
     *
     * <p>Uses both display_order and id for deterministic ordering: display_order is
     * not unique, so two rows can share the same value. Sorting by display_order alone
     * produces a PDF whose section order changes between renders.
     *
     * @param resumeId the resume ID
     * @return list of experience entries in display order, deterministically
     */
    List<ResumeExperience> findByResumeIdOrderByDisplayOrderAscIdAsc(Long resumeId);

    /**
     * Bulk deletes all experience entries for a resume.
     *
     * <p><b>CRITICAL:</b> The delete MUST be {@code @Modifying(flushAutomatically = true,
     * clearAutomatically = true)}. A derived method would let Hibernate order new INSERTs
     * before the DELETEs and blow up on the resume-id+name unique key. Without
     * {@code clearAutomatically}, the persistence context keeps serving rows the database
     * has already removed. This is used in a bulk replace (delete-then-reinsert) inside a
     * single transaction.
     *
     * @param resumeId the resume ID whose experience entries should be deleted
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ResumeExperience x where x.resume.id = :resumeId")
    void deleteAllByResumeId(@Param("resumeId") Long resumeId);
}
