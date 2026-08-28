import api from "@/services/api";
import type { Page, SubjectListParams, SubjectRequest, SubjectResponse } from "@/types/academic";
import { LOOKUP_PAGE_SIZE } from "@/services/departmentService";

/**
 * Typed wrappers around `/api/subjects`. See departmentService.ts for the general note
 * on this contract not yet being verified against a real backend controller.
 *
 *   GET    /api/subjects?page&size&sort&search&courseId&semester  -> 200 Page<SubjectResponse>  (ADMIN)
 *   POST   /api/subjects        SubjectRequest                     -> 201 SubjectResponse        (ADMIN)
 *   PUT    /api/subjects/{id}   SubjectRequest                     -> 200 SubjectResponse        (ADMIN)
 *   DELETE /api/subjects/{id}                                      -> 204                        (ADMIN)
 *
 * DELETE is a real hard delete — `subject_id` on `enrollments`/`faculty_subject_assignments`
 * has no ON DELETE clause (RESTRICT), so deleting a subject already enrolled/assigned
 * fails with a §47 error the UI surfaces as-is.
 */

const BASE = "/api/subjects";

export async function listSubjects(params: SubjectListParams = {}): Promise<Page<SubjectResponse>> {
  const { data } = await api.get<Page<SubjectResponse>>(BASE, { params });
  return data;
}

/** All subjects for one course — used by faculty-assignment style pickers. */
export async function listAllSubjectsForCourse(courseId: number): Promise<SubjectResponse[]> {
  const page = await listSubjects({ page: 0, size: LOOKUP_PAGE_SIZE, sort: "semester,asc", courseId });
  return page.content;
}

export async function createSubject(payload: SubjectRequest): Promise<SubjectResponse> {
  const { data } = await api.post<SubjectResponse>(BASE, payload);
  return data;
}

export async function updateSubject(id: number, payload: SubjectRequest): Promise<SubjectResponse> {
  const { data } = await api.put<SubjectResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteSubject(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
