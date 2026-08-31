import api from "@/services/api";
import type { ListParams, Page } from "@/types/academic";
import type { ExamRequest, ExamResponse, ExamStatus, ExamType, ExamUpdateRequest } from "@/types/academicOps";

/**
 * Typed wrappers around `/api/exams`, per the Phase 4 contract:
 *
 *   POST   /api/exams          ExamRequest       -> 201 ExamResponse
 *          (FACULTY, scoped by ScopedWriteAuthorizer against the request tuple, or ADMIN)
 *   GET    /api/exams          ?subjectId&academicYear&semester&section&examType&status&
 *          fromDate&toDate&search&page&size&sort -> 200 Page<ExamResponse> (any role).
 *          `search` matches the exam title, case-insensitive substring — the controller
 *          reads `search` directly (unlike StudentController's `q`), so it is passed
 *          through unchanged; default sort is `examDate,asc`.
 *   GET    /api/exams/{id}     -> 200 ExamResponse (any role)
 *   GET    /api/exams/upcoming ?limit=10 (max 50) -> 200 ExamResponse[] (any role) —
 *          scoped server-side to the caller: STUDENT sees exams for enrolled subjects,
 *          FACULTY sees exams matching their own assignment tuples, ADMIN sees all.
 *          Always SCHEDULED and examDate >= today, ordered by examDate ascending.
 *   PUT    /api/exams/{id}     ExamUpdateRequest -> 200 ExamResponse
 *          (FACULTY via guard on the EXISTING exam's tuple, or ADMIN). The scope tuple
 *          (subjectId/academicYear/semester/section) is immutable after creation — the
 *          backend DTO has no such fields, so this module never sends them on update.
 *   DELETE /api/exams/{id}     -> 204 (FACULTY via guard, or ADMIN); 409 if any marks
 *          row already references it — surface that via extractErrorMessage.
 */

const BASE = "/api/exams";

export interface ExamListParams extends ListParams {
  subjectId?: number;
  academicYear?: string;
  semester?: number;
  section?: string;
  examType?: ExamType;
  status?: ExamStatus;
  fromDate?: string;
  toDate?: string;
}

export async function listExams(params: ExamListParams = {}): Promise<Page<ExamResponse>> {
  const { data } = await api.get<Page<ExamResponse>>(BASE, { params });
  return data;
}

export async function getExam(id: number): Promise<ExamResponse> {
  const { data } = await api.get<ExamResponse>(`${BASE}/${id}`);
  return data;
}

export async function listUpcoming(limit = 10): Promise<ExamResponse[]> {
  const { data } = await api.get<ExamResponse[]>(`${BASE}/upcoming`, { params: { limit } });
  return data;
}

export async function createExam(payload: ExamRequest): Promise<ExamResponse> {
  const { data } = await api.post<ExamResponse>(BASE, payload);
  return data;
}

export async function updateExam(id: number, payload: ExamUpdateRequest): Promise<ExamResponse> {
  const { data } = await api.put<ExamResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteExam(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
