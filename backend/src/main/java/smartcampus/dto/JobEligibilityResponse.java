package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.JobStatus;

/**
 * Response for eligibility evaluation of a student against a job/drive (§34), including
 * the job details, student details, eligibility assessment, and the student's academic
 * metrics (CGPA, marks percentage, graduation year).
 */
public record JobEligibilityResponse(
    Long jobId,
    String jobTitle,
    Long companyId,
    String companyName,
    JobStatus jobStatus,
    LocalDateTime applicationDeadline,
    Long studentId,
    String registerNumber,
    String studentName,
    boolean eligible,
    boolean canApply,
    List<EligibilityReason> reasons,
    BigDecimal minCgpa,
    BigDecimal studentCgpa,
    BigDecimal minMarksPercentage,
    BigDecimal studentMarksPercentage,
    Integer requiredGraduationYear,
    Integer studentGraduationYear,
    List<JobDepartmentRef> eligibleDepartments,
    Long studentDepartmentId,
    String studentDepartmentName,
    Long existingApplicationId,
    ApplicationStatus existingApplicationStatus) {}
