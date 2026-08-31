import api from "@/services/api";
import type { Page } from "@/types/academic";
import type {
  AcademicResultResponse,
  MarksBulkRequest,
  MarksBulkResponse,
  MarksEntrySheetResponse,
  MarksResponse,
  MarksUpdateRequest,
} from "@/types/academicOps";

/**
 * Typed wrappers around `/api/marks`, per the Phase 4 contract:
 *
 *   POST /api/marks/bulk          MarksBulkRequest -> 200 MarksBulkResponse
 *        (FACULTY, scoped by ScopedWriteAuthorizer against the EXAM's tuple, or ADMIN)
 *        UPSERT semantics — 200, not 201.
 *   GET  /api/marks/entry-sheet   ?examId -> 200 MarksEntrySheetResponse
 *        (FACULTY via guard, or ADMIN)
 *   GET  /api/marks/exam/{examId} -> 200 MarksResponse[] (FACULTY via guard, or ADMIN)
 *   PUT  /api/marks/{id}          MarksUpdateRequest -> 200 MarksResponse
 *        (FACULTY via guard on the mark's exam tuple, or ADMIN)
 *   GET  /api/marks/me            ?academicYear&semester&subjectId&examId&page&size
 *        -> 200 Page<MarksResponse> (STUDENT, own rows only)
 *   GET  /api/marks/me/summary    ?academicYear&semester -> 200 AcademicResultResponse
 *        (STUDENT, own rows only; omit both query params for every year/semester + CGPA)
 *   GET  /api/marks/summary/{studentId} ?academicYear&semester
 *        -> 200 AcademicResultResponse (ADMIN only)
 */

const BASE = "/api/marks";

export async function getEntrySheet(examId: number): Promise<MarksEntrySheetResponse> {
  const { data } = await api.get<MarksEntrySheetResponse>(`${BASE}/entry-sheet`, { params: { examId } });
  return data;
}

export async function saveBulk(payload: MarksBulkRequest): Promise<MarksBulkResponse> {
  const { data } = await api.post<MarksBulkResponse>(`${BASE}/bulk`, payload);
  return data;
}

export async function updateMark(id: number, payload: MarksUpdateRequest): Promise<MarksResponse> {
  const { data } = await api.put<MarksResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function listByExam(examId: number): Promise<MarksResponse[]> {
  const { data } = await api.get<MarksResponse[]>(`${BASE}/exam/${examId}`);
  return data;
}

export interface MarksListMineParams {
  academicYear?: string;
  semester?: number;
  subjectId?: number;
  examId?: number;
  page?: number;
  size?: number;
}

export async function listMine(params: MarksListMineParams = {}): Promise<Page<MarksResponse>> {
  const { data } = await api.get<Page<MarksResponse>>(`${BASE}/me`, { params });
  return data;
}

export interface AcademicResultParams {
  academicYear?: string;
  semester?: number;
}

export async function getMySummary(params: AcademicResultParams = {}): Promise<AcademicResultResponse> {
  const { data } = await api.get<AcademicResultResponse>(`${BASE}/me/summary`, { params });
  return data;
}

/** ADMIN-only: academic result summary (grades/GPA/CGPA) for any student. */
export async function getSummaryForStudent(
  studentId: number,
  params: AcademicResultParams = {},
): Promise<AcademicResultResponse> {
  const { data } = await api.get<AcademicResultResponse>(`${BASE}/summary/${studentId}`, { params });
  return data;
}
