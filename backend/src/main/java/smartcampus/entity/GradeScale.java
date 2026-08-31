package smartcampus.entity;

/**
 * The scale for expressing a grade value in resume_educations.
 *
 * <p>Mirrors the {@code chk_resume_educations_grade_scale} CHECK constraint in
 * {@code V9__resume.sql}.
 */
public enum GradeScale {
    CGPA,
    PERCENTAGE
}
