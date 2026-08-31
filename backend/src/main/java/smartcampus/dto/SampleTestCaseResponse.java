package smartcampus.dto;

/**
 * One SAMPLE test case of a problem, shown to any caller reading {@link
 * ProblemDetailResponse}. Never carries a hidden case — hidden input/expected output is
 * only ever served by the ADMIN-only {@code GET /api/problems/{id}/test-cases} route
 * (see {@link TestCaseResponse}).
 */
public record SampleTestCaseResponse(Long id, Integer ordinal, String input, String expectedOutput) {}
