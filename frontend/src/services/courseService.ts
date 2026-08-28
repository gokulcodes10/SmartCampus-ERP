import api from "@/services/api";
import type { CourseListParams, CourseRequest, CourseResponse, Page } from "@/types/academic";
import { LOOKUP_PAGE_SIZE } from "@/services/departmentService";

/**
 * Typed wrappers around `/api/courses`. See departmentService.ts for the general note
 * on this contract not yet being verified against a real backend controller.
 *
 *   GET    /api/courses?page&size&sort&search&departmentId  -> 200 Page<CourseResponse>  (ADMIN)
 *   POST   /api/courses        CourseRequest                 -> 201 CourseResponse        (ADMIN)
 *   PUT    /api/courses/{id}   CourseRequest                 -> 200 CourseResponse        (ADMIN)
 *   DELETE /api/courses/{id}                                 -> 204                       (ADMIN)
 *
 * `departmentId` filters to one department's courses. DELETE is a real hard delete —
 * `course_id` on `subjects`/`students` has no ON DELETE clause (RESTRICT), so deleting a
 * course with subjects or students attached fails with a §47 error the UI surfaces as-is.
 */

const BASE = "/api/courses";

export async function listCourses(params: CourseListParams = {}): Promise<Page<CourseResponse>> {
  const { data } = await api.get<Page<CourseResponse>>(BASE, { params });
  return data;
}

/** All courses (optionally scoped to one department) — for subject/student pickers. */
export async function listAllCourses(departmentId?: number): Promise<CourseResponse[]> {
  const page = await listCourses({ page: 0, size: LOOKUP_PAGE_SIZE, sort: "name,asc", departmentId });
  return page.content;
}

export async function createCourse(payload: CourseRequest): Promise<CourseResponse> {
  const { data } = await api.post<CourseResponse>(BASE, payload);
  return data;
}

export async function updateCourse(id: number, payload: CourseRequest): Promise<CourseResponse> {
  const { data } = await api.put<CourseResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteCourse(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
