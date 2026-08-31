import api from "@/services/api";
import type {
  AnalyticsAdminResponse,
  AnalyticsClassResponse,
  AnalyticsFilterOptionsResponse,
  AnalyticsStudentResponse,
} from "@/types/analytics";

/**
 * Typed wrappers around `/api/analytics`, per the Phase 5 contract:
 *
 *   GET /api/analytics/me                    ?academicYear&semester&months
 *       -> 200 AnalyticsStudentResponse (STUDENT, own record only; a `studentId`
 *       query param is never honoured here).
 *   GET /api/analytics/students/{studentId}  ?academicYear&semester&months
 *       -> 200 AnalyticsStudentResponse (ADMIN only)
 *   GET /api/analytics/class                 ?courseId&subjectId&academicYear&semester&section&months
 *       -> 200 AnalyticsClassResponse (FACULTY, own assignments only via
 *       AcademicAccessGuard, or ADMIN). All params optional.
 *   GET /api/analytics/overview              ?departmentId&courseId&academicYear&semester&section&months
 *       -> 200 AnalyticsAdminResponse (ADMIN only). All params optional.
 *   GET /api/analytics/filters               -> 200 AnalyticsFilterOptionsResponse
 *       (FACULTY or ADMIN)
 *
 * `months` clamps server-side to a configured max — an out-of-range value is never
 * rejected, so callers don't need to validate it before sending. A `null` figure
 * anywhere in the response means "no denominator / no data" (§60/§69) and must
 * render as an explicit empty state, never as 0.
 */

const BASE = "/api/analytics";

export interface AnalyticsTrendParams {
  academicYear?: string;
  semester?: number;
  months?: number;
}

export async function getMyAnalytics(
  params: AnalyticsTrendParams = {},
): Promise<AnalyticsStudentResponse> {
  const { data } = await api.get<AnalyticsStudentResponse>(`${BASE}/me`, { params });
  return data;
}

export async function getStudentAnalytics(
  studentId: number,
  params: AnalyticsTrendParams = {},
): Promise<AnalyticsStudentResponse> {
  const { data } = await api.get<AnalyticsStudentResponse>(`${BASE}/students/${studentId}`, { params });
  return data;
}

export interface AnalyticsClassParams {
  courseId?: number;
  subjectId?: number;
  academicYear?: string;
  semester?: number;
  section?: string;
  months?: number;
}

export async function getClassAnalytics(
  params: AnalyticsClassParams = {},
): Promise<AnalyticsClassResponse> {
  const { data } = await api.get<AnalyticsClassResponse>(`${BASE}/class`, { params });
  return data;
}

export interface AnalyticsOverviewParams {
  departmentId?: number;
  courseId?: number;
  academicYear?: string;
  semester?: number;
  section?: string;
  months?: number;
}

export async function getOverview(
  params: AnalyticsOverviewParams = {},
): Promise<AnalyticsAdminResponse> {
  const { data } = await api.get<AnalyticsAdminResponse>(`${BASE}/overview`, { params });
  return data;
}

export async function getFilterOptions(): Promise<AnalyticsFilterOptionsResponse> {
  const { data } = await api.get<AnalyticsFilterOptionsResponse>(`${BASE}/filters`);
  return data;
}
