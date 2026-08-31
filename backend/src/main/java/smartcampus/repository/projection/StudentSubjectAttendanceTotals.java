package smartcampus.repository.projection;

/**
 * One (student, subject, academicYear, semester, section) row of attendance totals,
 * produced by {@code AnalyticsAttendanceRepository.cohortSubjectTotals}. Getter names
 * must match that query's JPQL {@code as} aliases exactly.
 *
 * <p>{@code departmentId}/{@code departmentCode}/{@code departmentName} are nullable —
 * {@code students.department_id} is nullable and the owning query left-joins it, so a
 * student with no department still appears in the cohort with these three {@code null}.
 * Every count getter is a wrapper {@link Long} because {@code sum()}/{@code count()}
 * over an empty group can return {@code null} or the row can be entirely absent from the
 * group (never a primitive that would NPE on unboxing).
 */
public interface StudentSubjectAttendanceTotals {

    Long getStudentId();

    String getRegisterNumber();

    String getStudentName();

    Long getDepartmentId();

    String getDepartmentCode();

    String getDepartmentName();

    Long getCourseId();

    String getCourseCode();

    String getCourseName();

    Long getSubjectId();

    String getSubjectCode();

    String getSubjectName();

    Integer getCredits();

    String getAcademicYear();

    Integer getSemester();

    String getSection();

    Long getTotalRecords();

    Long getHeldClasses();

    Long getAttendedClasses();

    Long getCancelledClasses();
}
