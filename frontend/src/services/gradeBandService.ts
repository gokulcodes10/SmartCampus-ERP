import api from "@/services/api";
import type { GradeBandRequest, GradeBandResponse } from "@/types/academicOps";

/**
 * Typed wrappers around `/api/grade-bands`, per the Phase 4 contract:
 *
 *   GET    /api/grade-bands      -> 200 GradeBandResponse[], ordered by minPercentage
 *          DESC (any authenticated role — students must be able to see the scale). NOT
 *          paginated, unlike every other Phase 4 list endpoint.
 *   POST   /api/grade-bands      GradeBandRequest -> 201 GradeBandResponse (ADMIN)
 *   PUT    /api/grade-bands/{id} GradeBandRequest -> 200 GradeBandResponse (ADMIN)
 *   DELETE /api/grade-bands/{id} -> 204 (ADMIN)
 *
 * The write endpoints are exported for the admin Grade Bands page (owned by a different
 * Phase 4 task) even though no page in this task calls them.
 */

const BASE = "/api/grade-bands";

export async function listGradeBands(): Promise<GradeBandResponse[]> {
  const { data } = await api.get<GradeBandResponse[]>(BASE);
  return data;
}

export async function createGradeBand(payload: GradeBandRequest): Promise<GradeBandResponse> {
  const { data } = await api.post<GradeBandResponse>(BASE, payload);
  return data;
}

export async function updateGradeBand(id: number, payload: GradeBandRequest): Promise<GradeBandResponse> {
  const { data } = await api.put<GradeBandResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteGradeBand(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
