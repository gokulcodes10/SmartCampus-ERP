package smartcampus.entity;

/**
 * The proficiency level for a skill in resume_skills.
 *
 * <p>Mirrors the {@code chk_resume_skills_proficiency} CHECK constraint in
 * {@code V9__resume.sql}.
 */
public enum SkillProficiency {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}
