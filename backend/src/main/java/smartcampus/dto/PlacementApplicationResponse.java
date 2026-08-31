package smartcampus.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.JobStatus;

/**
 * Response representing a student's application to a placement drive (§35, §36),
 * including flattened job, company, and student details, plus application lifecycle metadata.
 *
 * <p>{@code resumeId}/{@code resumeTitle} (Phase 9) are both {@code null} when no resume
 * is attached to this application.
 */
public record PlacementApplicationResponse(
    Long id,
    Long jobId,
    String jobTitle,
    Long companyId,
    String companyName,
    JobStatus jobStatus,
    LocalDateTime applicationDeadline,
    LocalDate driveDate,
    Long studentId,
    String registerNumber,
    String studentName,
    String studentEmail,
    Long departmentId,
    String departmentName,
    Long courseId,
    String courseName,
    Integer currentSemester,
    String section,
    ApplicationStatus status,
    String coverNote,
    Long resumeId,
    String resumeTitle,
    BigDecimal cgpaAtApplication,
    BigDecimal percentageAtApplication,
    LocalDateTime appliedAt,
    LocalDateTime statusChangedAt,
    Long statusChangedById,
    String statusChangedByName,
    String decisionNote,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
