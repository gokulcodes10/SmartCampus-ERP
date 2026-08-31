package smartcampus.entity;

/**
 * The visibility and recruitment status of a company in the placement module.
 *
 * <p>Stored as the string name (not the ordinal) in {@code companies.status}.
 *
 * <p><b>ACTIVE:</b> Company is recruiting and its drives are visible to students and admins.
 *
 * <p><b>INACTIVE:</b> Company is no longer recruiting. Its drives and applications remain
 * for the record but are hidden from the student-facing UI.
 */
public enum CompanyStatus {
    ACTIVE,
    INACTIVE
}
