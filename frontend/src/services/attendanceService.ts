import api from "@/services/api";
import type { Page } from "@/types/academic";
import type {
  AttendanceBulkRequest,
  AttendanceBulkResponse,
  AttendanceClassSummaryResponse,
  AttendanceResponse,
  AttendanceRosterResponse,
  AttendanceStatus,
  AttendanceSummaryResponse,
  AttendanceUpdateRequest,
} from "@/types/academicOps";

/**
 * Typed wrappers around `/api/attendance`, per the Phase 4 contract:
 *
 *   POST /api/attendance/bulk         AttendanceBulkRequest  -> 200 AttendanceBulkResponse
 *        (FACULTY, scoped by ScopedWriteAuthorizer against the request tuple, or ADMIN)
 *        UPSERT semantics — 200, not 201.
 *   GET  /api/attendance/roster       ?subjectId&academicYear&semester&section&date&period
 *        -> 200 AttendanceRosterResponse (FACULTY via guard, or ADMIN). All five query
 *        params are required.
 *   PUT  /api/attendance/{id}         AttendanceUpdateRequest -> 200 AttendanceResponse
 *        (FACULTY via guard on THAT ROW's own tuple, or ADMIN)
 *   GET  /api/attendance/me           ?academicYear&semester&subjectId&page&size
 *        -> 200 Page<AttendanceResponse> (STUDENT, own rows only)
 *   GET  /api/attendance/me/summary   ?academicYear&semester -> 200 AttendanceSummaryResponse
 *        (STUDENT, own rows only)
 *   GET  /api/attendance/summary/{studentId} ?academicYear&semester
 *        -> 200 AttendanceSummaryResponse (ADMIN only)
 *   GET  /api/attendance/class-summary ?subjectId&academicYear&semester&section
 *        -> 200 AttendanceClassSummaryResponse (FACULTY via guard, or ADMIN). All four
 *        query params are required.
 *
 * A `null` `attendancePercentage`/`overallPercentage` means "no classes held yet" (G6) —
 * callers must render that as an explicit "no classes held" state, never as 0% and never
 * with a low-attendance badge. `lowAttendance` is always `false` when the percentage is
 * `null`; trust the backend flag rather than recomputing the threshold on the client.
 */

const BASE = "/api/attendance";

export interface AttendanceRosterParams {
  subjectId: number;
  academicYear: string;
  semester: number;
  section: string;
  date: string;
  period: number;
}

export async function getRoster(params: AttendanceRosterParams): Promise<AttendanceRosterResponse> {
  const { data } = await api.get<AttendanceRosterResponse>(`${BASE}/roster`, { params });
  return data;
}

export async function markBulk(payload: AttendanceBulkRequest): Promise<AttendanceBulkResponse> {
  const { data } = await api.post<AttendanceBulkResponse>(`${BASE}/bulk`, payload);
  return data;
}

export async function updateAttendance(
  id: number,
  payload: AttendanceUpdateRequest,
): Promise<AttendanceResponse> {
  const { data } = await api.put<AttendanceResponse>(`${BASE}/${id}`, payload);
  return data;
}

export interface AttendanceListMineParams {
  academicYear?: string;
  semester?: number;
  subjectId?: number;
  page?: number;
  size?: number;
}

export async function listMine(params: AttendanceListMineParams = {}): Promise<Page<AttendanceResponse>> {
  const { data } = await api.get<Page<AttendanceResponse>>(`${BASE}/me`, { params });
  return data;
}

export interface AttendanceSummaryParams {
  academicYear?: string;
  semester?: number;
}

export async function getMySummary(
  params: AttendanceSummaryParams = {},
): Promise<AttendanceSummaryResponse> {
  const { data } = await api.get<AttendanceSummaryResponse>(`${BASE}/me/summary`, { params });
  return data;
}

/** ADMIN-only: attendance summary for any student. */
export async function getSummaryForStudent(
  studentId: number,
  params: AttendanceSummaryParams = {},
): Promise<AttendanceSummaryResponse> {
  const { data } = await api.get<AttendanceSummaryResponse>(`${BASE}/summary/${studentId}`, { params });
  return data;
}

export interface AttendanceClassSummaryParams {
  subjectId: number;
  academicYear: string;
  semester: number;
  section: string;
}

export async function getClassSummary(
  params: AttendanceClassSummaryParams,
): Promise<AttendanceClassSummaryResponse> {
  const { data } = await api.get<AttendanceClassSummaryResponse>(`${BASE}/class-summary`, { params });
  return data;
}

/** The full set of statuses a faculty member can mark a student as, in contract order. */
export const ATTENDANCE_STATUSES: AttendanceStatus[] = ["PRESENT", "ABSENT", "LATE", "ON_DUTY", "CANCELLED"];
