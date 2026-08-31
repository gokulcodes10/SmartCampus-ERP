package smartcampus.dto;

import java.util.List;

/**
 * Response for {@code POST /api/coding/problems/{problemId}/run} — the problem's
 * SAMPLE cases only, run against the caller's source. Nothing is persisted; this is a
 * scratch execution the student uses to sanity-check code before submitting.
 */
public record SampleRunResponse(List<SampleRunCaseResponse> cases, boolean allPassed) {}
