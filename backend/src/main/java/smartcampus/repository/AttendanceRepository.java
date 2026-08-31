package smartcampus.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import smartcampus.entity.Attendance;
import smartcampus.entity.AttendanceStatus;
import smartcampus.repository.projection.AttendanceStudentTotals;
import smartcampus.repository.projection.AttendanceSubjectTotals;

/**
 * Persistence access for {@link Attendance}.
 *
 * <p>{@link #summarizeByStudent} and {@link #summarizeByClass} compute the
 * attendance-percentage aggregates entirely in the database, grouped by subject or by
 * student respectively. Both accept the held/attended status sets as parameters rather
 * than hard-coding them, and each has a convenience default method that supplies those
 * sets from {@link AttendanceStatus#heldStatuses()} / {@link
 * AttendanceStatus#attendedStatuses()} — so no service anywhere restates the
 * PROJECT_PLAN.md clarification G6 rule.
 */
public interface AttendanceRepository
        extends JpaRepository<Attendance, Long>, JpaSpecificationExecutor<Attendance> {

    Optional<Attendance> findByStudentIdAndSubjectIdAndAttendanceDateAndPeriod(
            Long studentId, Long subjectId, LocalDate attendanceDate, Integer period);

    List<Attendance> findBySubjectIdAndAcademicYearAndSemesterAndSectionAndAttendanceDateAndPeriod(
            Long subjectId,
            String academicYear,
            Integer semester,
            String section,
            LocalDate attendanceDate,
            Integer period);

    @Query(
            """
            select s.id            as subjectId,
                   s.code          as subjectCode,
                   s.name          as subjectName,
                   s.credits       as credits,
                   a.academicYear  as academicYear,
                   a.semester      as semester,
                   count(a.id)                                                        as totalRecords,
                   sum(case when a.status in :heldStatuses     then 1 else 0 end)      as heldClasses,
                   sum(case when a.status in :attendedStatuses then 1 else 0 end)      as attendedClasses,
                   sum(case when a.status not in :heldStatuses then 1 else 0 end)      as cancelledClasses
            from Attendance a
            join a.subject s
            where a.student.id = :studentId
              and (:academicYear is null or a.academicYear = :academicYear)
              and (:semester is null or a.semester = :semester)
              and (:subjectId is null or s.id = :subjectId)
            group by s.id, s.code, s.name, s.credits, a.academicYear, a.semester
            order by a.academicYear asc, a.semester asc, s.code asc
            """)
    List<AttendanceSubjectTotals> summarizeByStudent(
            @Param("studentId") Long studentId,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("subjectId") Long subjectId,
            @Param("heldStatuses") Collection<AttendanceStatus> heldStatuses,
            @Param("attendedStatuses") Collection<AttendanceStatus> attendedStatuses);

    /** Supplies the held/attended sets from AttendanceStatus so no call site restates the rule. */
    default List<AttendanceSubjectTotals> summarizeByStudent(
            Long studentId, String academicYear, Integer semester, Long subjectId) {
        return summarizeByStudent(
                studentId,
                academicYear,
                semester,
                subjectId,
                AttendanceStatus.heldStatuses(),
                AttendanceStatus.attendedStatuses());
    }

    @Query(
            """
            select st.id                  as studentId,
                   st.registerNumber      as registerNumber,
                   u.fullName             as studentName,
                   count(a.id)                                                        as totalRecords,
                   sum(case when a.status in :heldStatuses     then 1 else 0 end)      as heldClasses,
                   sum(case when a.status in :attendedStatuses then 1 else 0 end)      as attendedClasses,
                   sum(case when a.status not in :heldStatuses then 1 else 0 end)      as cancelledClasses
            from Attendance a
            join a.student st
            join st.user u
            where a.subject.id = :subjectId
              and a.academicYear = :academicYear
              and a.semester = :semester
              and a.section = :section
            group by st.id, st.registerNumber, u.fullName
            order by st.registerNumber asc
            """)
    List<AttendanceStudentTotals> summarizeByClass(
            @Param("subjectId") Long subjectId,
            @Param("academicYear") String academicYear,
            @Param("semester") Integer semester,
            @Param("section") String section,
            @Param("heldStatuses") Collection<AttendanceStatus> heldStatuses,
            @Param("attendedStatuses") Collection<AttendanceStatus> attendedStatuses);

    default List<AttendanceStudentTotals> summarizeByClass(
            Long subjectId, String academicYear, Integer semester, String section) {
        return summarizeByClass(
                subjectId,
                academicYear,
                semester,
                section,
                AttendanceStatus.heldStatuses(),
                AttendanceStatus.attendedStatuses());
    }
}
