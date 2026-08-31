package smartcampus.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.InterviewQuestion;
import smartcampus.repository.projection.InterviewCategoryCount;

/**
 * Persistence access for {@link InterviewQuestion}.
 *
 * <p>{@link JpaSpecificationExecutor} supports dynamic filtering (category, difficulty,
 * company name, source, question text, bookmarked, completed, mine) and pagination for
 * {@code GET /api/interview-questions}.
 */
public interface InterviewQuestionRepository
    extends JpaRepository<InterviewQuestion, Long>,
        JpaSpecificationExecutor<InterviewQuestion> {

    /**
     * Find a curated question (global bank) by id.
     *
     * @param id the question id
     * @return the question if found and is a global bank row (owner_student_id IS NULL)
     */
    Optional<InterviewQuestion> findByIdAndOwnerStudentIsNull(Long id);

    /**
     * Find a private question (student-owned) by id.
     *
     * @param id the question id
     * @param studentId the owner student id
     * @return the question if found and belongs to the specified student
     */
    Optional<InterviewQuestion> findByIdAndOwnerStudentId(Long id, Long studentId);

    /**
     * Count visible questions by category for a student.
     *
     * <p>A question is visible to a student if it is in the global bank (owner_student_id
     * IS NULL) or if it is owned by the student themselves.
     *
     * @param studentId the student id
     * @return a list of category counts, one per visible category
     */
    @Query(
        "select q.category as category, count(q) as total from InterviewQuestion q "
            + "where q.ownerStudent is null or q.ownerStudent.id = :studentId "
            + "group by q.category")
    List<InterviewCategoryCount> countVisibleByCategory(@Param("studentId") Long studentId);

    /**
     * Count total visible questions for a student.
     *
     * @param studentId the student id
     * @return the count of questions visible to this student
     */
    @Query(
        "select count(q) from InterviewQuestion q "
            + "where q.ownerStudent is null or q.ownerStudent.id = :studentId")
    long countVisible(@Param("studentId") Long studentId);
}
