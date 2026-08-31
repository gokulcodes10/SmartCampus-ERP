package smartcampus.dto;

import java.util.List;

/**
 * Response from bulk status update operation (§36), including counts of requested,
 * successfully updated applications, and details of skipped applications.
 */
public record ApplicationBulkStatusResponse(
    int requested,
    int updated,
    List<BulkStatusSkip> skipped) {}
