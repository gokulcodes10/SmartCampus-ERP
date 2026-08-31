import api from "@/services/api";
import type {
  CodingStatsResponse,
  LanguageResponse,
  Page,
  RunRequest,
  RunResponse,
  SampleRunResponse,
  SubmissionCreateRequest,
  SubmissionDetailResponse,
  SubmissionListParams,
  SubmissionSummaryResponse,
} from "@/types/coding";

/**
 * Typed wrappers around `/api/coding` (PHASE 7 IMPLEMENTATION CONTRACT §9).
 *
 *   GET  /api/coding/languages                 -> 200 List<LanguageResponse>   any authenticated
 *   POST /api/coding/run            RunRequest -> 200 RunResponse              any authenticated
 *        Free-form single execution, nothing persisted. Throws (503,
 *        EXECUTION_UNAVAILABLE) when the judge is unreachable — which is the expected
 *        state on this machine (Judge0 has no reachable endpoint here, G10).
 *   POST /api/coding/problems/{problemId}/run  RunRequest -> 200 SampleRunResponse
 *        Runs the problem's SAMPLE cases only; `stdin` is ignored. Nothing persisted.
 *        Same 503 behaviour as /run.
 *   POST /api/coding/submissions  SubmissionCreateRequest -> 201 SubmissionDetailResponse  STUDENT
 *        ALWAYS 201 once the row exists, including when the judge is unreachable — in
 *        that case status is INTERNAL_ERROR with a populated errorMessage. Never throws
 *        for a judge failure; only a validation failure (bad problemId, no test cases,
 *        contest rules) throws before the row is created.
 *   GET  /api/coding/submissions
 *        params: page,size,sort,problemId,contestId,status,studentId
 *        -> 200 Page<SubmissionSummaryResponse>   STUDENT(own only) / ADMIN(any) / FACULTY 403
 *   GET  /api/coding/submissions/{id}  -> 200 SubmissionDetailResponse   owner or ADMIN; else 404
 *   GET  /api/coding/stats/me          -> 200 CodingStatsResponse        STUDENT
 *
 * NOT VERIFIED against a live backend — written against the contract while the
 * backend for this phase was being built concurrently. In particular: the exact 503
 * envelope shape from a Judge0 timeout, and whether FACULTY truly gets a plain 403
 * (vs. some other status) on the submissions endpoints, could not be exercised here.
 */

const CODING_BASE = "/api/coding";

export async function listLanguages(): Promise<LanguageResponse[]> {
  const { data } = await api.get<LanguageResponse[]>(`${CODING_BASE}/languages`);
  return data;
}

/** Free-form run against arbitrary stdin. No expected output, no persistence. */
export async function runCode(payload: RunRequest): Promise<RunResponse> {
  const { data } = await api.post<RunResponse>(`${CODING_BASE}/run`, payload);
  return data;
}

/** Runs a problem's SAMPLE cases only, at that problem's limits. `stdin` is ignored. */
export async function runSampleCases(
  problemId: number,
  payload: RunRequest,
): Promise<SampleRunResponse> {
  const { data } = await api.post<SampleRunResponse>(
    `${CODING_BASE}/problems/${problemId}/run`,
    payload,
  );
  return data;
}

export async function submitSolution(
  payload: SubmissionCreateRequest,
): Promise<SubmissionDetailResponse> {
  const { data } = await api.post<SubmissionDetailResponse>(`${CODING_BASE}/submissions`, payload);
  return data;
}

export async function listSubmissions(
  params: SubmissionListParams = {},
): Promise<Page<SubmissionSummaryResponse>> {
  const { data } = await api.get<Page<SubmissionSummaryResponse>>(`${CODING_BASE}/submissions`, {
    params,
  });
  return data;
}

export async function getSubmission(id: number): Promise<SubmissionDetailResponse> {
  const { data } = await api.get<SubmissionDetailResponse>(`${CODING_BASE}/submissions/${id}`);
  return data;
}

export async function getMyCodingStats(): Promise<CodingStatsResponse> {
  const { data } = await api.get<CodingStatsResponse>(`${CODING_BASE}/stats/me`);
  return data;
}
