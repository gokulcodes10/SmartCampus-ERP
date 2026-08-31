package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;
import smartcampus.entity.ApplicationStatus;

/**
 * One row from the eligible students list for a job/drive, including student profile,
 * academic metrics, eligibility assessment, and application status if already applied.
 */
public record EligibleStudentRow(
    Long studentId,
    String registerNumber,
    String studentName,
    String email,
    Long departmentId,
    String departmentName,
    Long courseId,
    String courseName,
    Integer currentSemester,
    String section,
    Integer graduationYear,
    BigDecimal cgpa,
    BigDecimal marksPercentage,
    boolean eligible,
    boolean hasApplied,
    ApplicationStatus applicationStatus,
    List<EligibilityReason> reasons) {}
