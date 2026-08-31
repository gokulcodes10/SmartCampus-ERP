import api from "@/services/api";
import type { GlobalLeaderboardRowResponse, LeaderboardListParams, Page } from "@/types/coding";

/**
 * Typed wrapper around `/api/leaderboard` (PHASE 7 IMPLEMENTATION CONTRACT §9).
 *
 *   GET /api/leaderboard/global?page&size -> 200 Page<GlobalLeaderboardRowResponse>
 *       any authenticated. Ranked by totalScore desc, problemsSolved desc,
 *       lastAcceptedAt asc, studentId asc — paginated server-side.
 *
 * NOT VERIFIED against a live backend — written against the contract while the
 * backend for this phase was being built concurrently.
 */

const BASE = "/api/leaderboard";

export async function getGlobalLeaderboard(
  params: LeaderboardListParams = {},
): Promise<Page<GlobalLeaderboardRowResponse>> {
  const { data } = await api.get<Page<GlobalLeaderboardRowResponse>>(`${BASE}/global`, { params });
  return data;
}
