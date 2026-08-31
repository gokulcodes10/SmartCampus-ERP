import api from "@/services/api";
import type { PlacementAnalyticsResponse } from "@/types/placement";

/**
 * Typed wrapper around `/api/placement/analytics`, per the Phase 8 contract:
 *
 *   GET /api/placement/analytics  -> 200 PlacementAnalyticsResponse   ADMIN
 *
 * Every figure comes straight from the response — nothing here is computed in the
 * browser (§10). `placementRate` (top-level and per-department) is `number | null`;
 * null means "no denominator yet" and must render as "Not enough data", never 0% (§69).
 */

const BASE = "/api/placement";

export async function getPlacementAnalytics(): Promise<PlacementAnalyticsResponse> {
  const { data } = await api.get<PlacementAnalyticsResponse>(`${BASE}/analytics`);
  return data;
}
