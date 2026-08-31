package smartcampus.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.Marks;
import smartcampus.repository.projection.MarksSubjectTotals;

/**
 * Persistence access for {@link Marks}.
 *
 * <p>{@link #summarizeByStudent} computes per-subject marks totals for one student
 * entirely in the database, excluding exams with a given {@link ExamStatus} — a
 * convenience default method supplies {@link ExamStatus#CANCELLED} so no service
 * anywhere restates the PROJECT_PLAN.md clarification G7 rule that a cancelled exam
 * never contributes to a grade.
 */
public interface MarksRepository extends JpaRepository<Marks, Long>, JpaSpecificationExecutor<Marks> {

    Optional<Marks> findByExamIdAndStudentId(Long examId, Long studentId);

    List<Marks> findByExamIdOrderByStudentId(Long examId);

    long countByExamId(Long examId);

    @Query("select coalesce(max(m.marksObtained), 0) from Marks m where m.exam.id = :examId")
    BigDecimal findHighestMarkForExam(@Param("examId") Long examId);

    @Query(
            """
            select s.id            as subjectId,
                   s.code          as subjectCode,
                   s.name          as subjectName,
                   s.credits       as credits,
                   e.academicYear  as academicYear,
                   e.semester      as semester,
                   count(m.id)          as examCount,
                   sum(m.marksObtained) as totalObtained,
                   sum(e.maximumMarks)  as totalMaximum
            from Marks m
            join m.exam e
            join e.subject s
            where m.student.id = :studentId
              and e.status <> :excludedStatus
              and (:academicYear is null or e.academicYear = :academicYear)
              and (:semester is null or e.semester = :semester)
            group by s.id, s.code, s.name, s.credits, e.academicYear, e.semester
            order by e.academicYear asc, e.semester asc, s.code asc
            """)
    List<MarksSubjectTotals> summarizeByStudent(
            @Param("studentId") Long studentId,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("excludedStatus") ExamStatus excludedStatus);

    /** A CANCELLED exam never contributes to a grade. */
    default List<MarksSubjectTotals> summarizeByStudent(
            Long studentId, String academicYear, Integer semester) {
        return summarizeByStudent(studentId, academicYear, semester, ExamStatus.CANCELLED);
    }
}
