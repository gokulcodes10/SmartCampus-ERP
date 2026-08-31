import api from "@/services/api";
import type { TeachingClassResponse } from "@/types/academicOps";

/**
 * Typed wrapper around `/api/teaching`, per the Phase 4 contract:
 *
 *   GET /api/teaching/my-classes -> 200 TeachingClassResponse[]   (FACULTY only;
 *       STUDENT and ADMIN get 403 — an admin manages assignments through the existing
 *       ADMIN-only /api/faculty-subject-assignments instead)
 *
 * This is how every faculty screen discovers the (subject, academicYear, semester,
 * section) tuples it is allowed to act on — see ClassScopePicker.tsx, the shared
 * control every Phase 4 faculty page uses to consume this list.
 */

const BASE = "/api/teaching";

export async function listMyClasses(): Promise<TeachingClassResponse[]> {
  const { data } = await api.get<TeachingClassResponse[]>(`${BASE}/my-classes`);
  return data;
}
