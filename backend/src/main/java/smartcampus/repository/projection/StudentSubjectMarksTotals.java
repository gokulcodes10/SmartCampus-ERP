package smartcampus.repository.projection;

import java.math.BigDecimal;

/**
 * One (student, subject, academicYear, semester, section) row of marks totals, produced
 * by {@code AnalyticsMarksRepository.cohortSubjectTotals}. Getter names must match that
 * query's JPQL {@code as} aliases exactly.
 *
 * <p>{@code departmentId}/{@code departmentCode}/{@code departmentName} are nullable —
 * {@code students.department_id} is nullable and the owning query left-joins it, so a
 * student with no department still appears in the cohort with these three {@code null}.
 * {@code totalObtained}/{@code totalMaximum} are wrapper {@link BigDecimal}s because
 * {@code sum()} over an empty group returns {@code null}.
 */
public interface StudentSubjectMarksTotals {

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

    Long getExamCount();

    BigDecimal getTotalObtained();

    BigDecimal getTotalMaximum();
}
