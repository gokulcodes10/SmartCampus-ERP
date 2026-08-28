import api from "@/services/api";
import type {
  Page,
  StudentActivateRequest,
  StudentListParams,
  StudentResponse,
  StudentUpdateRequest,
} from "@/types/academic";

/**
 * Typed wrappers around `/api/students`, reconciled against the real
 * `smartcampus.controller.StudentController`:
 *
 *   GET     /api/students?page&size&status&departmentId&courseId&currentSemester&section&q
 *           -> 200 Page<StudentResponse>   (ADMIN; a FACULTY caller is transparently
 *           narrowed to the students they teach by the same endpoint)
 *           `q` matches register number, email or full name. `status=PENDING` backs
 *           the G1 activation queue (`GET /api/students/pending` is a thin admin
 *           wrapper around the same filter). Note: the backend does not accept a
 *           `sort` parameter yet — results are always newest-first (`id DESC`).
 *   GET     /api/students/me            -> 200 StudentResponse (the caller's own row)
 *   GET     /api/students/{id}          -> 200 StudentResponse — ADMIN sees any row;
 *           STUDENT/FACULTY get a 404 (never a 403) on any id that is not theirs / not
 *           taught by them, so ID enumeration can't distinguish "not yours" from
 *           "doesn't exist".
 *   PUT     /api/students/{id}          StudentUpdateRequest -> 200 StudentResponse (ADMIN)
 *           Edits department/course/currentSemester/section/admissionYear only. Does
 *           **not** accept `registerNumber` or `status` — sending them is silently
 *           ignored by the backend (`StudentAdminUpdateRequest` has no such fields), so
 *           this module never sends them either.
 *   POST    /api/students/{id}/activate StudentActivateRequest -> 200 StudentResponse (ADMIN)
 *           G1: assigns department, course, register number and current semester to a
 *           PENDING student and flips status to ACTIVE, all required together (DB CHECK
 *           `chk_students_active_requires_assignment`). Re-activating an already-ACTIVE
 *           student is rejected with a clean §47 409.
 *   PATCH   /api/students/{id}/deactivate -> 200 StudentResponse (ADMIN) — ACTIVE/PENDING → INACTIVE.
 *   PATCH   /api/students/{id}/reactivate -> 200 StudentResponse (ADMIN) — INACTIVE → ACTIVE
 *           (only valid for a student who was previously assigned, since INACTIVE still
 *           satisfies the CHECK constraint with or without an assignment).
 *
 * There is no DELETE and no dedicated "toggle status" endpoint — deactivate/reactivate
 * are two distinct routes, not a status field on the PUT payload (see `deactivateStudent`
 * / `reactivateStudent` below).
 */

const BASE = "/api/students";

export async function listStudents(params: StudentListParams = {}): Promise<Page<StudentResponse>> {
  // useServerTable always sends the search box's value as `search` (shared across
  // every admin resource); StudentController's query param is named `q`, so it is
  // translated here rather than in the shared hook.
  const { search, sort: _sort, ...rest } = params;
  const { data } = await api.get<Page<StudentResponse>>(BASE, {
    params: { ...rest, q: search },
  });
  return data;
}

export async function activateStudent(
  id: number,
  payload: StudentActivateRequest,
): Promise<StudentResponse> {
  const { data } = await api.post<StudentResponse>(`${BASE}/${id}/activate`, payload);
  return data;
}

export async function updateStudent(
  id: number,
  payload: StudentUpdateRequest,
): Promise<StudentResponse> {
  const { data } = await api.put<StudentResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deactivateStudent(id: number): Promise<StudentResponse> {
  const { data } = await api.patch<StudentResponse>(`${BASE}/${id}/deactivate`);
  return data;
}

export async function reactivateStudent(id: number): Promise<StudentResponse> {
  const { data } = await api.patch<StudentResponse>(`${BASE}/${id}/reactivate`);
  return data;
}
