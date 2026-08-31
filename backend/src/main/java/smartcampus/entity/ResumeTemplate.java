package smartcampus.entity;

/**
 * The three PDF layout templates available for resumes.
 *
 * <p>Mirrors the {@code chk_resumes_template} CHECK constraint in
 * {@code V9__resume.sql}.
 */
public enum ResumeTemplate {
    CLASSIC,
    MODERN,
    COMPACT
}
