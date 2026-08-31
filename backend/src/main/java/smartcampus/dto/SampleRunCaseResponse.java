package smartcampus.dto;

import smartcampus.entity.SubmissionStatus;

/** One sample case's outcome inside {@link SampleRunResponse}. Nothing here is persisted. */
public record SampleRunCaseResponse(
        Integer ordinal,
        String input,
        String expectedOutput,
        String actualOutput,
        String stderr,
        SubmissionStatus status,
        boolean passed,
        Integer executionTimeMs,
        Integer memoryKb) {}
