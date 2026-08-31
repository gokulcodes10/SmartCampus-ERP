package smartcampus.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.FacultyStatus;
import smartcampus.entity.Student;
import smartcampus.entity.StudentStatus;

import java.util.List;

/**
 * Phase 5 analytics reads that are not scoped to one entity's aggregates: cohort
 * headcounts, the {@code /api/analytics/overview} platform totals, the subject-id
 * resolution used to build a scope, and the distinct filter-option lists shown on the
 * frontend filter bar. Extends the plain Spring Data {@link Repository} marker (not
 * {@code JpaRepository}) so it exposes only these reads.
 */
public interface AnalyticsCohortRepository extends Repository<Student, Long> {

    long countByStatus(StudentStatus status);

    /**
     * Subject ids in scope for an optional course and/or department filter.
     *
     * <p>Deliberately does NOT filter on {@code Subject.semester} — that is the
     * SYLLABUS semester a subject belongs to, not the class semester a student is
     * currently taking it in; filtering here would silently drop repeat/off-cycle
     * enrollments. The semester filter that matters for analytics is applied to the
     * attendance/exam rows instead (see {@link AnalyticsAttendanceRepository} and
     * {@link AnalyticsMarksRepository}).
     */
    @Query(
            "select s.id from Subject s where (:courseId is null or s.course.id = :courseId) "
                    + "and (:departmentId is null or s.course.department.id = :departmentId)")
    List<Long> findSubjectIds(@Param("courseId") Long courseId, @Param("departmentId") Long departmentId);

    /**
     * Distinct academic years actually in use, read from {@code enrollments} — the
     * roster — rather than a hard-coded list in Java or React.
     */
    @Query("select distinct en.academicYear from Enrollment en order by en.academicYear desc")
    List<String> findDistinctAcademicYears();

    /** Distinct semesters actually in use, read from {@code enrollments}. */
    @Query("select distinct en.semester from Enrollment en order by en.semester asc")
    List<Integer> findDistinctSemesters();

    /** Distinct sections actually in use, read from {@code enrollments}. */
    @Query("select distinct en.section from Enrollment en order by en.section asc")
    List<String> findDistinctSections();

    @Query("select count(s.id) from Student s")
    long countStudents();

    @Query("select count(f.id) from Faculty f")
    long countFaculty();

    @Query("select count(f.id) from Faculty f where f.status = :status")
    long countFacultyByStatus(@Param("status") FacultyStatus status);

    @Query("select count(d.id) from Department d")
    long countDepartments();

    @Query("select count(c.id) from Course c")
    long countCourses();

    @Query("select count(s.id) from Subject s")
    long countSubjects();

    /** A CANCELLED exam is still counted here — this is a scheduling total, not a grading aggregate. */
    @Query("select count(e.id) from Exam e where e.status <> :excludedStatus")
    long countExams(@Param("excludedStatus") ExamStatus excludedStatus);
}
