package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.JobStatus;
import smartcampus.entity.JobType;

/**
 * Response representing a job/placement drive (§33-§35), including flattened company details,
 * eligible departments, application counts, and metadata about whether it is currently accepting
 * applications.
 */
public record JobResponse(
    Long id,
    Long companyId,
    String companyName,
    String title,
    String description,
    String location,
    JobType jobType,
    Integer openings,
    BigDecimal salaryMin,
    BigDecimal salaryMax,
    String salaryCurrency,
    BigDecimal minCgpa,
    BigDecimal minMarksPercentage,
    Integer graduationYear,
    List<JobDepartmentRef> eligibleDepartments,
    LocalDateTime applicationDeadline,
    LocalDate driveDate,
    JobStatus status,
    boolean acceptingApplications,
    Long postedById,
    String postedByName,
    long applicationCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
