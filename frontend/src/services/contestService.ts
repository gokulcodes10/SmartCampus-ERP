import api from "@/services/api";
import type {
  ContestCreateRequest,
  ContestDetailResponse,
  ContestLeaderboardRowResponse,
  ContestListParams,
  ContestParticipantResponse,
  ContestProblemRequest,
  ContestProblemResponse,
  ContestSummaryResponse,
  ContestUpdateRequest,
  Page,
} from "@/types/coding";

/**
 * Typed wrappers around `/api/contests` (PHASE 7 IMPLEMENTATION CONTRACT §9).
 *
 *   GET    /api/contests?page&size&sort&search&status&phase
 *          -> 200 Page<ContestSummaryResponse>       any authenticated
 *          `status` is honoured for ADMIN only server-side.
 *   GET    /api/contests/{id}                 -> 200 ContestDetailResponse   any authenticated
 *          (DRAFT/CANCELLED -> 404 for non-admin; problems hidden — empty array,
 *          problemsVisible=false — for a non-admin caller while UPCOMING)
 *   POST   /api/contests   ContestCreateRequest -> 201 ContestDetailResponse ADMIN
 *   PUT    /api/contests/{id} ContestUpdateRequest -> 200 ContestDetailResponse ADMIN
 *   DELETE /api/contests/{id}                 -> 204                         ADMIN
 *   POST   /api/contests/{id}/problems  ContestProblemRequest -> 201 ContestProblemResponse ADMIN
 *   DELETE /api/contests/{contestId}/problems/{problemId} -> 204             ADMIN
 *   POST   /api/contests/{id}/register        -> 201 ContestParticipantResponse   STUDENT
 *   GET    /api/contests/{id}/me              -> 200 ContestParticipantResponse   STUDENT
 *                                                 404 when not registered
 *   GET    /api/contests/{id}/leaderboard?page&size
 *                                             -> 200 Page<ContestLeaderboardRowResponse>
 *                                                any authenticated
 *   POST   /api/contests/{id}/recompute       -> 200 ContestDetailResponse   ADMIN
 *
 * NOT VERIFIED against a live backend — written against the contract while the
 * backend for this phase was being built concurrently.
 */

const BASE = "/api/contests";

export async function listContests(params: ContestListParams = {}): Promise<Page<ContestSummaryResponse>> {
  const { data } = await api.get<Page<ContestSummaryResponse>>(BASE, { params });
  return data;
}

export async function getContest(id: number): Promise<ContestDetailResponse> {
  const { data } = await api.get<ContestDetailResponse>(`${BASE}/${id}`);
  return data;
}

export async function createContest(payload: ContestCreateRequest): Promise<ContestDetailResponse> {
  const { data } = await api.post<ContestDetailResponse>(BASE, payload);
  return data;
}

export async function updateContest(
  id: number,
  payload: ContestUpdateRequest,
): Promise<ContestDetailResponse> {
  const { data } = await api.put<ContestDetailResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteContest(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}

export async function addContestProblem(
  contestId: number,
  payload: ContestProblemRequest,
): Promise<ContestProblemResponse> {
  const { data } = await api.post<ContestProblemResponse>(`${BASE}/${contestId}/problems`, payload);
  return data;
}

export async function removeContestProblem(contestId: number, problemId: number): Promise<void> {
  await api.delete(`${BASE}/${contestId}/problems/${problemId}`);
}

export async function registerForContest(contestId: number): Promise<ContestParticipantResponse> {
  const { data } = await api.post<ContestParticipantResponse>(`${BASE}/${contestId}/register`);
  return data;
}

export async function getMyParticipation(contestId: number): Promise<ContestParticipantResponse> {
  const { data } = await api.get<ContestParticipantResponse>(`${BASE}/${contestId}/me`);
  return data;
}

export async function getContestLeaderboard(
  contestId: number,
  params: { page?: number; size?: number } = {},
): Promise<Page<ContestLeaderboardRowResponse>> {
  const { data } = await api.get<Page<ContestLeaderboardRowResponse>>(
    `${BASE}/${contestId}/leaderboard`,
    { params },
  );
  return data;
}

export async function recomputeContestLeaderboard(contestId: number): Promise<ContestDetailResponse> {
  const { data } = await api.post<ContestDetailResponse>(`${BASE}/${contestId}/recompute`);
  return data;
}
