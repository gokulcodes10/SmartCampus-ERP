package smartcampus.dto;

import java.time.LocalDateTime;
import java.util.List;
import smartcampus.entity.ResumeTemplate;

/**
 * The full assembled resume: header/contact block plus all six sections. Assembled
 * entirely inside {@code ResumeService}'s transaction (open-in-view is disabled). Every
 * section list is never {@code null} - an empty section is an empty list, not a missing
 * key. {@code locked} is {@code true} exactly when {@code lockedAt != null}.
 */
public record ResumeResponse(
    Long id,
    Long studentId,
    String title,
    ResumeTemplate template,
    String fullName,
    String email,
    String phone,
    String location,
    String linkedinUrl,
    String githubUrl,
    String portfolioUrl,
    String summary,
    boolean locked,
    LocalDateTime lockedAt,
    List<ResumeEducationResponse> educations,
    List<ResumeExperienceResponse> experiences,
    List<ResumeProjectResponse> projects,
    List<ResumeCertificationResponse> certifications,
    List<ResumeSkillResponse> skills,
    List<ResumeAchievementResponse> achievements,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
