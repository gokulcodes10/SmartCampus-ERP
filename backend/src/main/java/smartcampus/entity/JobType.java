package smartcampus.entity;

/**
 * The employment classification for a placement drive.
 *
 * <p>Stored as the string name (not the ordinal) in {@code jobs.job_type}.
 */
public enum JobType {
    FULL_TIME,
    PART_TIME,
    INTERNSHIP,
    CONTRACT
}
