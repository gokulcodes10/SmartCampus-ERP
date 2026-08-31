package smartcampus.service;

import smartcampus.entity.SubmissionStatus;

/**
 * The real, judge-produced verdict for one {@link ExecutionCase}. Every field beyond
 * {@code status} is exactly what Judge0 reported (decoded from base64 where
 * applicable) - nothing here is inferred or defaulted by the application.
 *
 * @param status                  already mapped from Judge0's {@code status.id}, see
 *                                 {@link Judge0Service} for the mapping table
 * @param judge0StatusId          Judge0's raw {@code status.id}
 * @param judge0StatusDescription Judge0's raw {@code status.description}
 * @param stdout                  decoded, may be null
 * @param stderr                  decoded, may be null
 * @param compileOutput           decoded, may be null
 * @param message                 Judge0's {@code message} field, decoded, may be null
 * @param executionTimeMs         null when Judge0 reported no time
 * @param memoryKb                null when Judge0 reported no memory
 * @param token                   the Judge0 submission token this result came from
 */
public record ExecutionResult(
        SubmissionStatus status,
        Integer judge0StatusId,
        String judge0StatusDescription,
        String stdout,
        String stderr,
        String compileOutput,
        String message,
        Integer executionTimeMs,
        Integer memoryKb,
        String token) {}
