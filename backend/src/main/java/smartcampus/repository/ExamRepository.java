package smartcampus.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import smartcampus.entity.Exam;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.ExamType;

/**
 * Persistence access for {@link Exam}.
 *
 * <p>{@link #findBySubjectIdInAndStatusAndExamDateGreaterThanEqualOrderByExamDateAscIdAsc}
 * and {@link #findByStatusAndExamDateGreaterThanEqualOrderByExamDateAscIdAsc} back the
 * "upcoming exams" queries for STUDENT/FACULTY and ADMIN respectively.
 */
public interface ExamRepository extends JpaRepository<Exam, Long>, JpaSpecificationExecutor<Exam> {

    Optional<Exam> findBySubjectIdAndAcademicYearAndSemesterAndSectionAndExamTypeAndTitle(
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            ExamType examType,
            String title);

    List<Exam> findBySubjectIdInAndStatusAndExamDateGreaterThanEqualOrderByExamDateAscIdAsc(
            Collection<Long> subjectIds, ExamStatus status, LocalDate from, Pageable pageable);

    List<Exam> findByStatusAndExamDateGreaterThanEqualOrderByExamDateAscIdAsc(
            ExamStatus status, LocalDate from, Pageable pageable);
}
