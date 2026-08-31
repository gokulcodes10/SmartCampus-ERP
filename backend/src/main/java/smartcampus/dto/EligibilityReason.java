package smartcampus.dto;

/**
 * A reason why a student is or is not eligible for a job/drive, including the criterion
 * code and human-readable message, plus optional requirement and actual values for display.
 */
public record EligibilityReason(
    EligibilityReasonCode code,
    String message,
    String requirement,
    String actual) {}
