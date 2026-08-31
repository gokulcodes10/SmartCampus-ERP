package smartcampus.dto;

/**
 * Details of an application skipped during bulk status update, including the application ID
 * and the reason it was skipped.
 */
public record BulkStatusSkip(
    Long applicationId,
    String reason) {}
