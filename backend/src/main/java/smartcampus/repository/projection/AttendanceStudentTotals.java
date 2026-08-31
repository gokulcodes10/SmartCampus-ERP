package smartcampus.repository.projection;

/**
 * Per-student attendance totals for one class (subject/year/semester/section), produced
 * by {@code AttendanceRepository.summarizeByClass}. Getter names must match that
 * query's JPQL {@code as} aliases exactly.
 */
public interface AttendanceStudentTotals {

    Long getStudentId();

    String getRegisterNumber();

    String getStudentName();

    Long getTotalRecords();

    Long getHeldClasses();

    Long getAttendedClasses();

    Long getCancelledClasses();
}
