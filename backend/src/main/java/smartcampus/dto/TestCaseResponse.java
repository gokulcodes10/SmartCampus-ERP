package smartcampus.dto;

import java.time.LocalDateTime;

/**
 * The full representation of one {@code problem_test_cases} row, INCLUDING input and
 * expected output regardless of {@code isSample}. Only ever returned by the
 * ADMIN-only {@code GET /api/problems/{id}/test-cases} family of routes — this is the
 * one place hidden-case data is allowed to leave the server.
 *
 * <p>The record component is named {@code isSample} (not {@code sample}) so the JSON
 * key the frontend reads is {@code isSample} — for a record, Jackson uses the
 * component name verbatim as the accessor/property name, unlike the entity's Lombok
 * {@code isSample()} getter for its {@code sample} field.
 */
public record TestCaseResponse(
        Long id,
        Long problemId,
        Integer ordinal,
        String input,
        String expectedOutput,
        boolean isSample,
        Integer weight,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
