package smartcampus.entity;

/**
 * The category for grouping skills in resume_skills.
 *
 * <p>Mirrors the {@code chk_resume_skills_category} CHECK constraint in
 * {@code V9__resume.sql}.
 */
public enum SkillCategory {
    TECHNICAL,
    TOOL,
    LANGUAGE,
    SOFT
}
