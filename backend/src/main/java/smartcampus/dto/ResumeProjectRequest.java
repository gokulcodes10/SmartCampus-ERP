package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * One project entry submitted as part of {@link ResumeSaveRequest}. Date ordering (when
 * both dates are present) is enforced in {@code ResumeService} before any save, mirroring
 * {@code chk_resume_projects_date_order} in V9__resume.sql.
 */
public record ResumeProjectRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 20000) String description,
    @Size(max = 255) String techStack,
    @Size(max = 255) String projectUrl,
    @Size(max = 255) String repositoryUrl,
    LocalDate startDate,
    LocalDate endDate) {}
