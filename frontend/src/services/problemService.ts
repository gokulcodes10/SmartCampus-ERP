import api from "@/services/api";
import type {
  Page,
  ProblemCreateRequest,
  ProblemDetailResponse,
  ProblemListParams,
  ProblemSummaryResponse,
  ProblemUpdateRequest,
  TestCaseRequest,
  TestCaseResponse,
} from "@/types/coding";

/**
 * Typed wrappers around `/api/problems` (PHASE 7 IMPLEMENTATION CONTRACT §9).
 *
 *   GET    /api/problems?page&size&sort&search&difficulty&tag&published
 *          -> 200 Page<ProblemSummaryResponse>              any authenticated
 *          `published` is honoured for ADMIN only; forced to true for everyone else.
 *   GET    /api/problems/{id}          -> 200 ProblemDetailResponse   any authenticated
 *          (unpublished -> 404 for non-admin, per the project's ENUMERATION RULE)
 *   POST   /api/problems   ProblemCreateRequest -> 201 ProblemDetailResponse   ADMIN
 *   PUT    /api/problems/{id} ProblemUpdateRequest -> 200 ProblemDetailResponse ADMIN
 *   DELETE /api/problems/{id}          -> 204                                  ADMIN
 *          RESTRICTed by the DB if referenced by a submission or contest; surfaced as
 *          a §47 error (409) by the shared handler.
 *   GET    /api/problems/{id}/test-cases                -> 200 List<TestCaseResponse>  ADMIN ONLY
 *   POST   /api/problems/{id}/test-cases  TestCaseRequest -> 201 TestCaseResponse       ADMIN
 *   PUT    /api/problems/{problemId}/test-cases/{testCaseId} TestCaseRequest -> 200     ADMIN
 *   DELETE /api/problems/{problemId}/test-cases/{testCaseId} -> 204                     ADMIN
 *
 * NOT VERIFIED against a live backend — written against the contract while the
 * backend for this phase was being built concurrently.
 */

const BASE = "/api/problems";

export async function listProblems(params: ProblemListParams = {}): Promise<Page<ProblemSummaryResponse>> {
  const { data } = await api.get<Page<ProblemSummaryResponse>>(BASE, { params });
  return data;
}

export async function getProblem(id: number): Promise<ProblemDetailResponse> {
  const { data } = await api.get<ProblemDetailResponse>(`${BASE}/${id}`);
  return data;
}

export async function createProblem(payload: ProblemCreateRequest): Promise<ProblemDetailResponse> {
  const { data } = await api.post<ProblemDetailResponse>(BASE, payload);
  return data;
}

export async function updateProblem(
  id: number,
  payload: ProblemUpdateRequest,
): Promise<ProblemDetailResponse> {
  const { data } = await api.put<ProblemDetailResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteProblem(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}

export async function listTestCases(problemId: number): Promise<TestCaseResponse[]> {
  const { data } = await api.get<TestCaseResponse[]>(`${BASE}/${problemId}/test-cases`);
  return data;
}

export async function createTestCase(
  problemId: number,
  payload: TestCaseRequest,
): Promise<TestCaseResponse> {
  const { data } = await api.post<TestCaseResponse>(`${BASE}/${problemId}/test-cases`, payload);
  return data;
}

export async function updateTestCase(
  problemId: number,
  testCaseId: number,
  payload: TestCaseRequest,
): Promise<TestCaseResponse> {
  const { data } = await api.put<TestCaseResponse>(
    `${BASE}/${problemId}/test-cases/${testCaseId}`,
    payload,
  );
  return data;
}

export async function deleteTestCase(problemId: number, testCaseId: number): Promise<void> {
  await api.delete(`${BASE}/${problemId}/test-cases/${testCaseId}`);
}
