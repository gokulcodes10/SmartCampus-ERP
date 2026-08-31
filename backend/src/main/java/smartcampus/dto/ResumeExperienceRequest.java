package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import smartcampus.entity.EmploymentType;

/**
 * One work-experience entry submitted as part of {@link ResumeSaveRequest}. The
 * {@code currentPosition}/{@code endDate} agreement and date ordering that Bean
 * Validation cannot express are enforced in {@code ResumeService} before any save,
 * mirroring the {@code resume_experiences} CHECK constraints in V9__resume.sql.
 */
public record ResumeExperienceRequest(
    @NotBlank @Size(max = 200) String companyName,
    @NotBlank @Size(max = 150) String roleTitle,
    @Size(max = 150) String location,
    EmploymentType employmentType,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    boolean currentPosition,
    @Size(max = 20000) String description) {}
