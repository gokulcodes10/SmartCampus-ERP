package smartcampus.dto;

import smartcampus.entity.SubmissionStatus;

/**
 * Response for {@code POST /api/coding/run} — nothing is persisted, this is the raw
 * judge verdict for one free-form execution against caller-supplied stdin.
 */
public record RunResponse(
        SubmissionStatus status,
        Integer judge0StatusId,
        String judge0StatusDescription,
        String stdout,
        String stderr,
        String compileOutput,
        String message,
        Integer executionTimeMs,
        Integer memoryKb) {}
