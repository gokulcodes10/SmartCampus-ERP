import api from "@/services/api";
import type { PerformanceBandResponse } from "@/types/analytics";

/**
 * Typed wrappers around `/api/performance-bands`, per the Phase 5 contract:
 *
 *   GET /api/performance-bands      -> 200 PerformanceBandResponse[] (any authenticated
 *        role — a student must be able to see the scale they were judged by). Ordered
 *        by displayOrder ASC, not paginated (max 4 rows).
 *   PUT /api/performance-bands/{id} PerformanceBandUpdatePayload -> 200 PerformanceBandResponse
 *        (ADMIN only)
 *
 * The category set is closed — there is no create and no delete endpoint. The PUT body
 * must carry EXACTLY the five fields below and nothing else: the backend request record
 * does not declare `category` or `displayOrder`, and Jackson silently drops any field it
 * doesn't recognize, which is how Phase 3 shipped a toggle that appeared to work and
 * changed nothing.
 */

const BASE = "/api/performance-bands";

export interface PerformanceBandUpdatePayload {
  minMarksPercentage: number;
  minAttendancePercentage: number;
  minGpa: number | null;
  colorHex: string;
  description: string | null;
}

export async function list(): Promise<PerformanceBandResponse[]> {
  const { data } = await api.get<PerformanceBandResponse[]>(BASE);
  return data;
}

export async function update(
  id: number,
  payload: PerformanceBandUpdatePayload,
): Promise<PerformanceBandResponse> {
  const { data } = await api.put<PerformanceBandResponse>(`${BASE}/${id}`, payload);
  return data;
}
