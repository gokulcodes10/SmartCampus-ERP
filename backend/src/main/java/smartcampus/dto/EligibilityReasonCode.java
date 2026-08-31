package smartcampus.dto;

/**
 * Reason codes for why a student is or is not eligible for a placement drive.
 *
 * <p><b>CRITERION CODES (1-8):</b> Any presence indicates {@code eligible = false}.
 * These reflect failing a hard eligibility criterion.
 * <ul>
 *   <li>PROFILE_NOT_ACTIVE: Student profile is not ACTIVE
 *   <li>DEPARTMENT_NOT_ELIGIBLE: Student's department is not in the drive's eligible list
 *   <li>GRADUATION_YEAR_UNKNOWN: Graduation year cannot be determined from profile data
 *   <li>GRADUATION_YEAR_MISMATCH: Student's graduation year does not match the drive's
 *   <li>CGPA_NOT_AVAILABLE: Drive requires CGPA but student has no graded subjects
 *   <li>CGPA_BELOW_MINIMUM: Student's CGPA is below the drive's minimum
 *   <li>PERCENTAGE_NOT_AVAILABLE: Drive requires marks percentage but student has no marks
 *   <li>PERCENTAGE_BELOW_MINIMUM: Student's marks percentage is below the drive's minimum
 * </ul>
 *
 * <p><b>BLOCKER CODES (9-11):</b> Never change {@code eligible}; only affect {@code canApply}.
 * These reflect procedural or timing issues, not a failure of criteria.
 * <ul>
 *   <li>DRIVE_NOT_OPEN: Drive status is not OPEN
 *   <li>DEADLINE_PASSED: Application deadline has passed
 *   <li>ALREADY_APPLIED: Student has already applied to this drive
 * </ul>
 */
public enum EligibilityReasonCode {
    // Criterion codes (1-8): eligible = false if any present
    PROFILE_NOT_ACTIVE,
    DEPARTMENT_NOT_ELIGIBLE,
    GRADUATION_YEAR_UNKNOWN,
    GRADUATION_YEAR_MISMATCH,
    CGPA_NOT_AVAILABLE,
    CGPA_BELOW_MINIMUM,
    PERCENTAGE_NOT_AVAILABLE,
    PERCENTAGE_BELOW_MINIMUM,
    // Blocker codes (9-11): never change eligible, only canApply
    DRIVE_NOT_OPEN,
    DEADLINE_PASSED,
    ALREADY_APPLIED
}
