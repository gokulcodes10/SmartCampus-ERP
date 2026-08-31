package smartcampus.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for creating/updating one {@code problem_test_cases} row (ADMIN only).
 *
 * <p>{@code input} and {@code expectedOutput} use {@code @NotNull}, not
 * {@code @NotBlank}: an empty string is a legitimate test case (a program that reads
 * nothing, or whose correct output is nothing), and the database column itself is
 * {@code NOT NULL} but happily stores {@code ""} — see V7__coding.sql.
 */
public record TestCaseRequest(
        @NotNull @Min(1) Integer ordinal,
        @NotNull String input,
        @NotNull String expectedOutput,
        @NotNull Boolean isSample,
        @NotNull @Min(1) Integer weight) {}
