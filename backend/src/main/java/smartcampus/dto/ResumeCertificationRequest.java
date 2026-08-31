package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * One certification entry submitted as part of {@link ResumeSaveRequest}. Date ordering
 * (when both dates are present) is enforced in {@code ResumeService} before any save,
 * mirroring {@code chk_resume_certifications_date_order} in V9__resume.sql.
 */
public record ResumeCertificationRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 200) String issuer,
    LocalDate issueDate,
    LocalDate expiryDate,
    @Size(max = 120) String credentialId,
    @Size(max = 255) String credentialUrl) {}
