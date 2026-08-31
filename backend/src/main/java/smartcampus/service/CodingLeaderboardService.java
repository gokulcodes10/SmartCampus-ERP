package smartcampus.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.GlobalLeaderboardRowResponse;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Student;
import smartcampus.entity.User;
import smartcampus.repository.StudentRepository;

/**
 * {@code GET /api/leaderboard/global} — every student's global standing across
 * practice AND contest submissions.
 *
 * <p><b>The double-counting trap.</b> {@code problemsSolved} is {@code
 * COUNT(DISTINCT problem_id)} over a student's ACCEPTED submissions, and {@code
 * totalScore} sums each DISTINCT solved problem's difficulty points exactly once. A
 * naive {@code sum(case difficulty ...)} over raw submission rows (see the caution on
 * {@code CodingSubmissionRepository.globalLeaderboardRaw}) would score a problem once
 * per accepted submission, so a student who resubmits an already-accepted solution
 * three times would be awarded triple points — an invented number with no meaning
 * (§69). JPQL cannot express the required derived-table aggregation (a GROUP BY over
 * DISTINCT (student, problem, difficulty) pairs, then a second GROUP BY over student),
 * so this is a native query built and paginated entirely in SQL — never "load
 * everything and slice in memory" (§44).
 *
 * <p>Difficulty points are configuration, bound as query parameters, never baked in as
 * literal SQL (§60). Every {@code @Value} below carries an in-code default because
 * {@code src/test/resources/application.properties} shadows (does not merge with) the
 * main configuration file, so a property defined only in {@code src/main} would be
 * invisible to the test context and this bean would fail to construct there.
 */
@Service
public class CodingLeaderboardService {

    private final StudentRepository studentRepository;

    @PersistenceContext private EntityManager entityManager;

    private final int easyPoints;
    private final int mediumPoints;
    private final int hardPoints;

    public CodingLeaderboardService(
            StudentRepository studentRepository,
            @Value("${smartcampus.coding.points.easy:10}") int easyPoints,
            @Value("${smartcampus.coding.points.medium:20}") int mediumPoints,
            @Value("${smartcampus.coding.points.hard:30}") int hardPoints) {
        this.studentRepository = studentRepository;
        this.easyPoints = easyPoints;
        this.mediumPoints = mediumPoints;
        this.hardPoints = hardPoints;
    }

    private static final String PER_STUDENT_PROBLEM_SQL =
            """
            select s.student_id as student_id,
                   s.problem_id as problem_id,
                   max(s.created_at) as accepted_time,
                   case p.difficulty
                        when 'EASY' then :easy
                        when 'MEDIUM' then :medium
                        else :hard
                   end as points
            from coding_submissions s
            join coding_problems p on p.id = s.problem_id
            where s.status = 'ACCEPTED'
            group by s.student_id, s.problem_id, p.difficulty
            """;

    private static final String COUNT_SQL =
            "select count(*) from (select s.student_id from coding_submissions s "
                    + "where s.status = 'ACCEPTED' group by s.student_id) counted";

    private static final String PAGE_SQL =
            """
            select agg.student_id, agg.problems_solved, agg.total_score, agg.last_accepted_at
            from (
                select per_problem.student_id as student_id,
                       count(*) as problems_solved,
                       sum(per_problem.points) as total_score,
                       max(per_problem.accepted_time) as last_accepted_at
                from ("""
                    + PER_STUDENT_PROBLEM_SQL
                    + """
                ) per_problem
                group by per_problem.student_id
            ) agg
            order by agg.total_score desc, agg.problems_solved desc,
                     agg.last_accepted_at asc, agg.student_id asc
            limit :pageLimit offset :pageOffset
            """;

    @Transactional(readOnly = true)
    public PageResponse<GlobalLeaderboardRowResponse> globalLeaderboard(Pageable pageable) {
        Query countQuery = entityManager.createNativeQuery(COUNT_SQL);
        long totalElements = ((Number) countQuery.getSingleResult()).longValue();

        Query pageQuery =
                entityManager
                        .createNativeQuery(PAGE_SQL)
                        .setParameter("easy", easyPoints)
                        .setParameter("medium", mediumPoints)
                        .setParameter("hard", hardPoints)
                        .setParameter("pageLimit", pageable.getPageSize())
                        .setParameter("pageOffset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = pageQuery.getResultList();

        long offset = pageable.getOffset();
        List<GlobalLeaderboardRowResponse> content = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            Long studentId = ((Number) row[0]).longValue();
            long problemsSolved = ((Number) row[1]).longValue();
            long totalScore = ((Number) row[2]).longValue();
            LocalDateTime lastAcceptedAt = toLocalDateTime(row[3]);

            Student student = studentRepository.findById(studentId).orElse(null);
            User user = student != null ? student.getUser() : null;
            var department = student != null ? student.getDepartment() : null;

            content.add(
                    new GlobalLeaderboardRowResponse(
                            (int) (offset + i + 1),
                            studentId,
                            user != null ? user.getFullName() : null,
                            student != null ? student.getRegisterNumber() : null,
                            department != null ? department.getName() : null,
                            problemsSolved,
                            totalScore,
                            lastAcceptedAt));
        }

        int pageSize = pageable.getPageSize();
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return new PageResponse<>(content, pageable.getPageNumber(), pageSize, totalElements, totalPages);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        throw new IllegalStateException(
                "Unexpected last_accepted_at type from native query: " + value.getClass());
    }
}
