package smartcampus.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import smartcampus.entity.Interview;
import smartcampus.entity.InterviewStatus;

/**
 * Persistence access for {@link Interview}.
 *
 * <p>{@link JpaSpecificationExecutor} supports dynamic filtering (status, student,
 * date range, search) and pagination for {@code GET /api/interviews}.
 */
public interface InterviewRepository
    extends JpaRepository<Interview, Long>,
        JpaSpecificationExecutor<Interview> {

    /**
     * Find an interview with its student and student's user loaded.
     *
     * <p>Uses an EntityGraph to eagerly fetch the student and user relationships to avoid
     * N+1 queries during serialization.
     *
     * @param id the interview id
     * @return the interview with student and user loaded, if found
     */
    @EntityGraph(attributePaths = {"student", "student.user"})
    Optional<Interview> findWithStudentById(Long id);

    /**
     * Find an interview that belongs to a specific student.
     *
     * @param id the interview id
     * @param studentId the student id
     * @return the interview if found and belongs to the specified student
     */
    Optional<Interview> findByIdAndStudentId(Long id, Long studentId);

    /**
     * Find and lock overlapping interviews for conflict detection.
     *
     * <p>Uses PESSIMISTIC_WRITE locking to acquire InnoDB next-key/gap locks over the
     * (student_id, scheduled_start) range, preventing concurrent scheduling requests from
     * both passing the overlap check. A second concurrent request blocks rather than also
     * passing the check.
     *
     * <p>MUST be called from a read-write {@code @Transactional} method, never a
     * {@code readOnly=true} transaction.
     *
     * <p>Two interviews OVERLAP when
     * {@code a.scheduledStart < b.scheduledEnd AND a.scheduledEnd > b.scheduledStart}, so
     * back-to-back interviews (one ending exactly when the next begins) do NOT conflict.
     *
     * @param studentId the student id
     * @param blocking the interview statuses that block scheduling (typically SCHEDULED and
     *     RESCHEDULED)
     * @param start the start of the proposed window
     * @param end the end of the proposed window
     * @return all overlapping interviews with one of the blocking statuses
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select i from Interview i where i.student.id = :studentId "
            + "and i.status in :blocking "
            + "and i.scheduledStart < :end and i.scheduledEnd > :start")
    List<Interview> lockOverlapping(
        @Param("studentId") Long studentId,
        @Param("blocking") Collection<InterviewStatus> blocking,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    /**
     * Find upcoming interviews for a student.
     *
     * <p>Fetches SCHEDULED and RESCHEDULED interviews starting at or after the given time,
     * ordered by scheduled start.
     *
     * @param studentId the student id
     * @param statuses the statuses to filter by
     * @param from the earliest scheduled start time
     * @param pageable pagination info
     * @return the matching interviews in ascending order by scheduled start
     */
    @EntityGraph(attributePaths = {"student", "student.user"})
    List<Interview> findByStudentIdAndStatusInAndScheduledStartGreaterThanEqualOrderByScheduledStartAsc(
        Long studentId, Collection<InterviewStatus> statuses, LocalDateTime from, Pageable pageable);
}
