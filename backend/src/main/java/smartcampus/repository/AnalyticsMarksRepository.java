package smartcampus.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.ExamStatus;
import smartcampus.entity.Marks;
import smartcampus.repository.projection.ExamMarksTotals;
import smartcampus.repository.projection.MarksTrendTotals;
import smartcampus.repository.projection.StudentSubjectMarksTotals;

/**
 * Phase 5 analytics reads over {@link Marks}, entirely additive to {@link
 * MarksRepository} — this interface extends the plain Spring Data {@link Repository}
 * marker (not {@code JpaRepository}), so it exposes only the read aggregates below and
 * nothing that could mutate a row.
 *
 * <p>Every method here excludes a given {@link ExamStatus} via {@code @Param}, and each
 * has a {@code default} overload supplying {@link ExamStatus#CANCELLED} — so, exactly as
 * in {@link MarksRepository}, no service anywhere restates the PROJECT_PLAN.md
 * clarification G7 rule that a cancelled exam never contributes to a grade.
 *
 * <p>{@code subjectIds} parameters are NEVER null and NEVER empty in a well-formed call
 * — Hibernate cannot render {@code in ()} — so callers (the analytics service layer)
 * MUST short-circuit to an empty response before invoking {@link #trendByScope}, {@link
 * #cohortSubjectTotals} or {@link #examTotals}.
 */
public interface AnalyticsMarksRepository extends Repository<Marks, Long> {

    /**
     * Month-bucketed (by exam date) marks totals for one student, from {@code fromDate}
     * onward, optionally narrowed by academic year and/or semester.
     */
    @Query(
            """
            select year(e.examDate) as periodYear, month(e.examDate) as periodMonth,
                   count(m.id) as examCount, sum(m.marksObtained) as totalObtained,
                   sum(e.maximumMarks) as totalMaximum
            from Marks m join m.exam e
            where m.student.id = :studentId and e.status <> :excludedStatus
              and e.examDate >= :fromDate
              and (:academicYear is null or e.academicYear = :academicYear)
              and (:semester is null or e.semester = :semester)
            group by year(e.examDate), month(e.examDate)
            order by year(e.examDate) asc, month(e.examDate) asc
            """)
    List<MarksTrendTotals> trendByStudent(
            @Param("studentId") Long studentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("excludedStatus") ExamStatus excludedStatus);

    /** Supplies {@link ExamStatus#CANCELLED}; see the class javadoc. */
    default List<MarksTrendTotals> trendByStudent(
            Long studentId, LocalDate fromDate, String academicYear, Integer semester) {
        return trendByStudent(studentId, fromDate, academicYear, semester, ExamStatus.CANCELLED);
    }

    /**
     * Month-bucketed (by exam date) marks totals across a set of subjects (a faculty's
     * or admin's scope), from {@code fromDate} onward, optionally narrowed by academic
     * year, semester and/or section.
     */
    @Query(
            """
            select year(e.examDate) as periodYear, month(e.examDate) as periodMonth,
                   count(m.id) as examCount, sum(m.marksObtained) as totalObtained,
                   sum(e.maximumMarks) as totalMaximum
            from Marks m join m.exam e
            where e.subject.id in :subjectIds and e.status <> :excludedStatus
              and e.examDate >= :fromDate
              and (:academicYear is null or e.academicYear = :academicYear)
              and (:semester is null or e.semester = :semester)
              and (:section is null or e.section = :section)
            group by year(e.examDate), month(e.examDate)
            order by year(e.examDate) asc, month(e.examDate) asc
            """)
    List<MarksTrendTotals> trendByScope(
            @Param("subjectIds") Collection<Long> subjectIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("section") String section,
            @Param("excludedStatus") ExamStatus excludedStatus);

    /** Supplies {@link ExamStatus#CANCELLED}; see the class javadoc. */
    default List<MarksTrendTotals> trendByScope(
            Collection<Long> subjectIds,
            LocalDate fromDate,
            String academicYear,
            Integer semester,
            String section) {
        return trendByScope(subjectIds, fromDate, academicYear, semester, section, ExamStatus.CANCELLED);
    }

    /**
     * One row per (student, subject, academicYear, semester, section) across a set of
     * subjects, with exam count and obtained/maximum totals — the per-student,
     * per-subject building block the cohort ({@code /api/analytics/class}, {@code
     * /api/analytics/overview}) rollups use to build each student's {@code
     * SubjectGradeSummary} list and run {@code GradeCalculationService.creditWeightedGpa}
     * without N+1 queries.
     *
     * <p>{@code sum(e.maximumMarks)} is the sum of the maximums of exactly the exams the
     * student has a {@link Marks} row for — the same denominator {@link
     * MarksRepository#summarizeByStudent} already uses — never every exam scheduled for
     * the subject, some of which the student may not yet have a mark for.
     *
     * <p>{@code left join st.department d} is deliberate, not an oversight —
     * {@code students.department_id} is nullable, and an inner join would silently drop
     * a student with no department from the cohort entirely rather than surfacing them
     * with a null department.
     *
     * <p>The G7 cancelled-exam rule is still not restated here — it is supplied by the
     * {@code default} overload via {@link ExamStatus#CANCELLED}.
     */
    @Query(
            """
            select st.id as studentId, st.registerNumber as registerNumber, u.fullName as studentName,
                   d.id as departmentId, d.code as departmentCode, d.name as departmentName,
                   c.id as courseId, c.code as courseCode, c.name as courseName,
                   s.id as subjectId, s.code as subjectCode, s.name as subjectName,
                   s.credits as credits, e.academicYear as academicYear, e.semester as semester,
                   e.section as section,
                   count(m.id) as examCount, sum(m.marksObtained) as totalObtained,
                   sum(e.maximumMarks) as totalMaximum
            from Marks m
            join m.exam e
            join e.subject s
            join s.course c
            join m.student st
            join st.user u
            left join st.department d
            where s.id in :subjectIds and e.status <> :excludedStatus
              and (:academicYear is null or e.academicYear = :academicYear)
              and (:semester is null or e.semester = :semester)
              and (:section is null or e.section = :section)
            group by st.id, st.registerNumber, u.fullName, d.id, d.code, d.name,
                     c.id, c.code, c.name, s.id, s.code, s.name, s.credits,
                     e.academicYear, e.semester, e.section
            order by st.registerNumber asc, s.code asc
            """)
    List<StudentSubjectMarksTotals> cohortSubjectTotals(
            @Param("subjectIds") Collection<Long> subjectIds,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("section") String section,
            @Param("excludedStatus") ExamStatus excludedStatus);

    /** Supplies {@link ExamStatus#CANCELLED}; see the class javadoc. */
    default List<StudentSubjectMarksTotals> cohortSubjectTotals(
            Collection<Long> subjectIds, String academicYear, Integer semester, String section) {
        return cohortSubjectTotals(subjectIds, academicYear, semester, section, ExamStatus.CANCELLED);
    }

    /**
     * Per-exam totals (entered count, obtained sum, highest, lowest) across a set of
     * subjects, optionally narrowed by academic year, semester and/or section — backs
     * the class analytics view's per-exam averages.
     */
    @Query(
            """
            select e.id as examId, e.title as title, e.examType as examType,
                   e.examDate as examDate, e.maximumMarks as maximumMarks,
                   count(m.id) as marksEnteredCount, sum(m.marksObtained) as totalObtained,
                   max(m.marksObtained) as highestObtained, min(m.marksObtained) as lowestObtained
            from Marks m join m.exam e
            where e.subject.id in :subjectIds and e.status <> :excludedStatus
              and (:academicYear is null or e.academicYear = :academicYear)
              and (:semester is null or e.semester = :semester)
              and (:section is null or e.section = :section)
            group by e.id, e.title, e.examType, e.examDate, e.maximumMarks
            order by e.examDate asc, e.id asc
            """)
    List<ExamMarksTotals> examTotals(
            @Param("subjectIds") Collection<Long> subjectIds,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("section") String section,
            @Param("excludedStatus") ExamStatus excludedStatus);

    /** Supplies {@link ExamStatus#CANCELLED}; see the class javadoc. */
    default List<ExamMarksTotals> examTotals(
            Collection<Long> subjectIds, String academicYear, Integer semester, String section) {
        return examTotals(subjectIds, academicYear, semester, section, ExamStatus.CANCELLED);
    }
}
