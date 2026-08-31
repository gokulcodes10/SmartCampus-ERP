package smartcampus.repository.projection;

import java.math.BigDecimal;

/**
 * Per-subject marks totals for one student, produced by {@code
 * MarksRepository.summarizeByStudent}. Getter names must match that query's JPQL
 * {@code as} aliases exactly.
 */
public interface MarksSubjectTotals {

    Long getSubjectId();

    String getSubjectCode();

    String getSubjectName();

    Integer getCredits();

    String getAcademicYear();

    Integer getSemester();

    Long getExamCount();

    BigDecimal getTotalObtained();

    BigDecimal getTotalMaximum();
}
