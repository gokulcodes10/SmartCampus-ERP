package smartcampus.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.Attendance;
import smartcampus.entity.AttendanceStatus;
import smartcampus.repository.projection.AttendanceTrendTotals;
import smartcampus.repository.projection.StudentSubjectAttendanceTotals;

/**
 * Phase 5 analytics reads over {@link Attendance}, entirely additive to {@link
 * AttendanceRepository} — this interface extends the plain Spring Data {@link
 * Repository} marker (not {@code JpaRepository}), so it exposes only the read
 * aggregates below and nothing that could mutate a row.
 *
 * <p>Every method here accepts the held/attended status sets as {@code @Param}s rather
 * than hard-coding them, and each has a {@code default} overload supplying those sets
 * from {@link AttendanceStatus#heldStatuses()} / {@link
 * AttendanceStatus#attendedStatuses()} — so, exactly as in {@link AttendanceRepository},
 * no service anywhere restates the PROJECT_PLAN.md clarification G6 rule.
 *
 * <p>{@code subjectIds} parameters are NEVER null and NEVER empty in a well-formed call
 * — Hibernate cannot render {@code in ()} — so callers (the analytics service layer)
 * MUST short-circuit to an empty response before invoking {@link #trendByScope} or
 * {@link #cohortSubjectTotals}.
 */
public interface AnalyticsAttendanceRepository extends Repository<Attendance, Long> {

    /**
     * Month-bucketed held/attended totals for one student, from {@code fromDate}
     * onward, optionally narrowed by academic year and/or semester. Backed by the new
     * {@code idx_attendance_student_date} index (V5__analytics.sql), which exists
     * specifically for this student-scoped time-series scan.
     */
    @Query(
            """
            select year(a.attendanceDate) as periodYear, month(a.attendanceDate) as periodMonth,
                   sum(case when a.status in :heldStatuses then 1 else 0 end)     as heldClasses,
                   sum(case when a.status in :attendedStatuses then 1 else 0 end) as attendedClasses
            from Attendance a
            where a.student.id = :studentId
              and a.attendanceDate >= :fromDate
              and (:academicYear is null or a.academicYear = :academicYear)
              and (:semester is null or a.semester = :semester)
            group by year(a.attendanceDate), month(a.attendanceDate)
            order by year(a.attendanceDate) asc, month(a.attendanceDate) asc
            """)
    List<AttendanceTrendTotals> trendByStudent(
            @Param("studentId") Long studentId,
            @Param("fromDate") LocalDate fromDate,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("heldStatuses") Collection<AttendanceStatus> heldStatuses,
            @Param("attendedStatuses") Collection<AttendanceStatus> attendedStatuses);

    /** Supplies the held/attended sets from {@link AttendanceStatus}; see the class javadoc. */
    default List<AttendanceTrendTotals> trendByStudent(
            Long studentId, LocalDate fromDate, String academicYear, Integer semester) {
        return trendByStudent(
                studentId,
                fromDate,
                academicYear,
                semester,
                AttendanceStatus.heldStatuses(),
                AttendanceStatus.attendedStatuses());
    }

    /**
     * Month-bucketed held/attended totals across a set of subjects (a faculty's or
     * admin's scope), from {@code fromDate} onward, optionally narrowed by academic
     * year, semester and/or section.
     */
    @Query(
            """
            select year(a.attendanceDate) as periodYear, month(a.attendanceDate) as periodMonth,
                   sum(case when a.status in :heldStatuses then 1 else 0 end)     as heldClasses,
                   sum(case when a.status in :attendedStatuses then 1 else 0 end) as attendedClasses
            from Attendance a
            where a.subject.id in :subjectIds
              and a.attendanceDate >= :fromDate
              and (:academicYear is null or a.academicYear = :academicYear)
              and (:semester is null or a.semester = :semester)
              and (:section is null or a.section = :section)
            group by year(a.attendanceDate), month(a.attendanceDate)
            order by year(a.attendanceDate) asc, month(a.attendanceDate) asc
            """)
    List<AttendanceTrendTotals> trendByScope(
            @Param("subjectIds") Collection<Long> subjectIds,
            @Param("fromDate") LocalDate fromDate,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("section") String section,
            @Param("heldStatuses") Collection<AttendanceStatus> heldStatuses,
            @Param("attendedStatuses") Collection<AttendanceStatus> attendedStatuses);

    /** Supplies the held/attended sets from {@link AttendanceStatus}; see the class javadoc. */
    default List<AttendanceTrendTotals> trendByScope(
            Collection<Long> subjectIds,
            LocalDate fromDate,
            String academicYear,
            Integer semester,
            String section) {
        return trendByScope(
                subjectIds,
                fromDate,
                academicYear,
                semester,
                section,
                AttendanceStatus.heldStatuses(),
                AttendanceStatus.attendedStatuses());
    }

    /**
     * One row per (student, subject, academicYear, semester, section) across a set of
     * subjects, with held/attended/cancelled totals — the per-student, per-subject
     * building block the cohort ({@code /api/analytics/class}, {@code
     * /api/analytics/overview}) rollups group and re-aggregate in Java.
     *
     * <p>This is deliberately a sibling of {@link AttendanceRepository#summarizeByClass}
     * rather than a reuse of it: {@code summarizeByClass} requires subject, academic
     * year, semester AND section to all be non-null (it targets one exact class), while
     * the Phase 5 faculty/admin analytics filter set leaves academic year, semester and
     * section optional and spans potentially many subjects at once. This query also
     * carries the department/course/section columns the cohort rollups group and
     * classify by, which {@code summarizeByClass} has no need for.
     *
     * <p>{@code left join st.department d} is deliberate, not an oversight —
     * {@code students.department_id} is nullable, and an inner join would silently drop
     * a student with no department from the cohort entirely rather than surfacing them
     * with a null department.
     *
     * <p>The G6 held/attended rule is still not restated here — it is supplied by the
     * {@code default} overload from {@link AttendanceStatus}.
     */
    @Query(
            """
            select st.id as studentId, st.registerNumber as registerNumber, u.fullName as studentName,
                   d.id as departmentId, d.code as departmentCode, d.name as departmentName,
                   c.id as courseId, c.code as courseCode, c.name as courseName,
                   s.id as subjectId, s.code as subjectCode, s.name as subjectName,
                   s.credits as credits, a.academicYear as academicYear, a.semester as semester,
                   a.section as section,
                   count(a.id) as totalRecords,
                   sum(case when a.status in :heldStatuses then 1 else 0 end) as heldClasses,
                   sum(case when a.status in :attendedStatuses then 1 else 0 end) as attendedClasses,
                   sum(case when a.status not in :heldStatuses then 1 else 0 end) as cancelledClasses
            from Attendance a
            join a.student st
            join st.user u
            join a.subject s
            join s.course c
            left join st.department d
            where s.id in :subjectIds
              and (:academicYear is null or a.academicYear = :academicYear)
              and (:semester is null or a.semester = :semester)
              and (:section is null or a.section = :section)
            group by st.id, st.registerNumber, u.fullName, d.id, d.code, d.name,
                     c.id, c.code, c.name, s.id, s.code, s.name, s.credits,
                     a.academicYear, a.semester, a.section
            order by st.registerNumber asc, s.code asc
            """)
    List<StudentSubjectAttendanceTotals> cohortSubjectTotals(
            @Param("subjectIds") Collection<Long> subjectIds,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("section") String section,
            @Param("heldStatuses") Collection<AttendanceStatus> heldStatuses,
            @Param("attendedStatuses") Collection<AttendanceStatus> attendedStatuses);

    /** Supplies the held/attended sets from {@link AttendanceStatus}; see the class javadoc. */
    default List<StudentSubjectAttendanceTotals> cohortSubjectTotals(
            Collection<Long> subjectIds, String academicYear, Integer semester, String section) {
        return cohortSubjectTotals(
                subjectIds,
                academicYear,
                semester,
                section,
                AttendanceStatus.heldStatuses(),
                AttendanceStatus.attendedStatuses());
    }
}
