package smartcampus.dto;

import smartcampus.entity.SubmissionStatus;

/**
 * One per-test-case result inside {@link SubmissionDetailResponse}.
 *
 * <p>G3 / hidden-case redaction: for a HIDDEN case ({@code isSample == false}) shown
 * to a non-ADMIN caller, {@code input}, {@code expectedOutput}, {@code actualOutput}
 * and {@code stderrOutput} are ALL {@code null}. Only {@code ordinal}, {@code
 * isSample}, {@code status}, {@code passed} and the timings are revealed. An ADMIN
 * caller (the {@code revealHidden} flag on {@code CodingSubmissionRecorder.detail})
 * sees everything. {@code passed} is derived as {@code status == ACCEPTED} — there is
 * no {@code passed} column in the database.
 */
public record SubmissionTestResultResponse(
        Integer ordinal,
        boolean isSample,
        SubmissionStatus status,
        boolean passed,
        Integer executionTimeMs,
        Integer memoryKb,
        String input,
        String expectedOutput,
        String actualOutput,
        String stderrOutput) {}
