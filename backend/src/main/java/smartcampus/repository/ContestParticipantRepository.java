package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import smartcampus.entity.ContestParticipant;

/**
 * Persistence access for {@link ContestParticipant}.
 *
 * <p>{@link #findByContestIdOrderByTotalScoreDescPenaltySecondsAscLastAcceptedAtAscStudentIdAsc}
 * is the per-contest leaderboard ordering (ICPC-style: score desc, penalty asc, first
 * tiebreak asc, student id asc for a fully deterministic rank), served without a
 * filesort by {@code idx_contest_participants_leaderboard}.
 */
public interface ContestParticipantRepository extends JpaRepository<ContestParticipant, Long> {

    Optional<ContestParticipant> findByContestIdAndStudentId(Long contestId, Long studentId);

    boolean existsByContestIdAndStudentId(Long contestId, Long studentId);

    List<ContestParticipant> findByContestId(Long contestId);

    Page<ContestParticipant> findByContestIdOrderByTotalScoreDescPenaltySecondsAscLastAcceptedAtAscStudentIdAsc(
            Long contestId, Pageable pageable);

    long countByContestId(Long contestId);
}
