package smartcampus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import smartcampus.entity.ResumeTemplate;

/**
 * The header block plus every section of one resume version, used by BOTH
 * {@code POST /api/resumes} (create) and {@code PUT /api/resumes/{id}} (update, wholesale
 * replace). A {@code null} section list means "this section is empty" - {@code
 * ResumeService} treats {@code null} and {@code []} identically.
 *
 * <p>None of these lists carries a {@code displayOrder} field: the array order IS the
 * order. {@code ResumeService} assigns {@code displayOrder} as each element's index in
 * its list and ignores anything a client might otherwise send.
 */
public record ResumeSaveRequest(
    @NotBlank @Size(max = 150) String title,
    @NotNull ResumeTemplate template,
    @NotBlank @Size(max = 150) String fullName,
    @NotBlank @Email @Size(max = 255) String email,
    @Size(max = 20) String phone,
    @Size(max = 150) String location,
    @Size(max = 255) String linkedinUrl,
    @Size(max = 255) String githubUrl,
    @Size(max = 255) String portfolioUrl,
    @Size(max = 20000) String summary,
    @Valid List<ResumeEducationRequest> educations,
    @Valid List<ResumeExperienceRequest> experiences,
    @Valid List<ResumeProjectRequest> projects,
    @Valid List<ResumeCertificationRequest> certifications,
    @Valid List<ResumeSkillRequest> skills,
    @Valid List<ResumeAchievementRequest> achievements) {}
