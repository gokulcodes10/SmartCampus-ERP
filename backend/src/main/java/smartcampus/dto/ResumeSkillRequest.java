package smartcampus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import smartcampus.entity.SkillCategory;
import smartcampus.entity.SkillProficiency;

/**
 * One skill entry submitted as part of {@link ResumeSaveRequest}. Case-insensitive
 * duplicate names within one request are rejected in {@code ResumeService} before any
 * save, matching {@code uk_resume_skills_resume_name}'s utf8mb4_unicode_ci collation in
 * V9__resume.sql.
 */
public record ResumeSkillRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull SkillCategory category,
    SkillProficiency proficiency) {}
