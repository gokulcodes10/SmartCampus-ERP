package smartcampus.repository.projection;

/**
 * Per-subject attendance totals for one student, produced by {@code
 * AttendanceRepository.summarizeByStudent}. Getter names must match that query's JPQL
 * {@code as} aliases exactly.
 */
public interface AttendanceSubjectTotals {

    Long getSubjectId();

    String getSubjectCode();

    String getSubjectName();

    Integer getCredits();

    String getAcademicYear();

    Integer getSemester();

    Long getTotalRecords();

    Long getHeldClasses();

    Long getAttendedClasses();

    Long getCancelledClasses();
}
