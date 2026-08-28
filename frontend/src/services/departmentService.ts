import api from "@/services/api";
import type { DepartmentListParams, DepartmentRequest, DepartmentResponse, Page } from "@/types/academic";

/**
 * Typed wrappers around `/api/departments` — the Phase 3 admin CRUD contract this
 * frontend codes against. The Phase 3 backend controller did not exist yet when this
 * was written (this agent owns FRONTEND only); reconcile against the real
 * `DepartmentController` once it lands. See this agent's final report for the exact
 * contract expected.
 *
 *   GET    /api/departments?page&size&sort&search  -> 200 Page<DepartmentResponse>  (ADMIN)
 *   GET    /api/departments/{id}                    -> 200 DepartmentResponse        (ADMIN)
 *   POST   /api/departments        DepartmentRequest -> 201 DepartmentResponse        (ADMIN)
 *   PUT    /api/departments/{id}   DepartmentRequest -> 200 DepartmentResponse        (ADMIN)
 *   DELETE /api/departments/{id}                     -> 204                          (ADMIN)
 *
 * `search` matches against code/name. DELETE is a real hard delete — `department_id` on
 * `courses` has no ON DELETE clause (default RESTRICT), so deleting a department with
 * courses attached fails with a §47 409/400 the UI surfaces as-is.
 */

const BASE = "/api/departments";

/** The page size used when a form needs "every department" for a picker, not a table. */
export const LOOKUP_PAGE_SIZE = 200;

export async function listDepartments(params: DepartmentListParams = {}): Promise<Page<DepartmentResponse>> {
  const { data } = await api.get<Page<DepartmentResponse>>(BASE, { params });
  return data;
}

/** All departments, sorted by name — for populating course/student/faculty pickers. */
export async function listAllDepartments(): Promise<DepartmentResponse[]> {
  const page = await listDepartments({ page: 0, size: LOOKUP_PAGE_SIZE, sort: "name,asc" });
  return page.content;
}

export async function createDepartment(payload: DepartmentRequest): Promise<DepartmentResponse> {
  const { data } = await api.post<DepartmentResponse>(BASE, payload);
  return data;
}

export async function updateDepartment(
  id: number,
  payload: DepartmentRequest,
): Promise<DepartmentResponse> {
  const { data } = await api.put<DepartmentResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteDepartment(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
