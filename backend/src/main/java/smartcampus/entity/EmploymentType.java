package smartcampus.entity;

/**
 * The type of employment for a resume_experiences entry.
 *
 * <p>Mirrors the {@code chk_resume_experiences_employment_type} CHECK constraint in
 * {@code V9__resume.sql}.
 */
public enum EmploymentType {
    INTERNSHIP,
    FULL_TIME,
    PART_TIME,
    FREELANCE,
    VOLUNTEER
}
