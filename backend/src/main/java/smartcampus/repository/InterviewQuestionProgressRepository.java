package smartcampus.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.InterviewQuestionProgress;
import smartcampus.repository.projection.InterviewCategoryCount;

/**
 * Persistence access for {@link InterviewQuestionProgress}.
 *
 * <p>Manages progress tracking for interview questions: completion marking, bookmarks,
 * and completion timestamps.
 */
public interface InterviewQuestionProgressRepository
    extends JpaRepository<InterviewQuestionProgress, Long> {

    /**
     * Find or create the progress row for a (student, question) pair.
     *
     * @param studentId the student id
     * @param questionId the question id
     * @return the progress row if it exists
     */
    Optional<InterviewQuestionProgress> findByStudentIdAndQuestionId(Long studentId, Long questionId);

    /**
     * Find progress rows for a student and a collection of questions.
     *
     * <p>Used to bulk-fetch progress flags after loading a page of questions.
     *
     * @param studentId the student id
     * @param questionIds the question ids
     * @return all progress rows for the given (student, questions) combinations
     */
    List<InterviewQuestionProgress> findByStudentIdAndQuestionIdIn(
        Long studentId, Collection<Long> questionIds);

    /**
     * Count completed questions for a student.
     *
     * @param studentId the student id
     * @return the number of questions marked completed by this student
     */
    long countByStudentIdAndCompletedTrue(Long studentId);

    /**
     * Count bookmarked questions for a student.
     *
     * @param studentId the student id
     * @return the number of questions bookmarked by this student
     */
    long countByStudentIdAndBookmarkedTrue(Long studentId);

    /**
     * Count completed questions by category for a student.
     *
     * @param studentId the student id
     * @return a list of category counts for completed questions, one per category with at
     *     least one completion
     */
    @Query(
        "select p.question.category as category, count(p) as total "
            + "from InterviewQuestionProgress p "
            + "where p.student.id = :studentId and p.completed = true "
            + "group by p.question.category")
    List<InterviewCategoryCount> countCompletedByCategory(@Param("studentId") Long studentId);
}
