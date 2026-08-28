/**
 * Types for the Phase 3 (Core Academic) admin API: departments, courses, subjects,
 * students and faculty. These mirror the seven-table schema in
 * `backend/src/main/resources/db/migration/V3__academic.sql` and the entity contract
 * handed down for Phase 3 (see PROJECT_PLAN.md §3 and the build-agent context brief).
 *
 * Reconciled against the real backend response DTOs by the wave integrator (they were
 * written against a designed contract before the backend existed, per the build
 * report). The actual controllers return **flat** shapes — a `xId`/`xName` pair,
 * matching `smartcampus.dto.*Response` — never a nested `{ id, code, name }` object,
 * so every response type below mirrors that flat convention exactly. Do not reintroduce
 * nested `department`/`course`/`user` objects; the backend does not send them.
 */

/** Server-side pagination envelope (§44), used by every admin list endpoint. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Common query params accepted by every paginated admin list endpoint. */
export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
}

export type StudentStatus = "PENDING" | "ACTIVE" | "INACTIVE";
export type FacultyStatus = "ACTIVE" | "INACTIVE";
export type EnrollmentStatus = "ACTIVE" | "COMPLETED" | "DROPPED";

// ---------------------------------------------------------------------------------
// Department
// ---------------------------------------------------------------------------------

export interface DepartmentResponse {
  id: number;
  code: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}

export interface DepartmentRequest {
  code: string;
  name: string;
}

export interface DepartmentListParams extends ListParams {}

// ---------------------------------------------------------------------------------
// Course
// ---------------------------------------------------------------------------------

export interface CourseResponse {
  id: number;
  code: string;
  name: string;
  departmentId: number;
  departmentName: string;
  durationSemesters: number;
  createdAt: string;
  updatedAt: string;
}

export interface CourseRequest {
  code: string;
  name: string;
  departmentId: number;
  durationSemesters: number;
}

export interface CourseListParams extends ListParams {
  departmentId?: number;
}

// ---------------------------------------------------------------------------------
// Subject
// ---------------------------------------------------------------------------------

export interface SubjectResponse {
  id: number;
  code: string;
  name: string;
  credits: number;
  semester: number;
  courseId: number;
  courseCode: string;
  courseName: string;
  createdAt: string;
  updatedAt: string;
}

export interface SubjectRequest {
  code: string;
  name: string;
  credits: number;
  semester: number;
  courseId: number;
}

export interface SubjectListParams extends ListParams {
  courseId?: number;
  semester?: number;
}

// ---------------------------------------------------------------------------------
// Student
// ---------------------------------------------------------------------------------

export interface StudentResponse {
  id: number;
  userId: number;
  email: string;
  fullName: string;
  registerNumber: string | null;
  departmentId: number | null;
  departmentName: string | null;
  courseId: number | null;
  courseName: string | null;
  currentSemester: number | null;
  section: string | null;
  admissionYear: number | null;
  status: StudentStatus;
  createdAt: string;
  updatedAt: string;
}

export interface StudentListParams extends ListParams {
  status?: StudentStatus;
  departmentId?: number;
  courseId?: number;
  currentSemester?: number;
  section?: string;
}

/**
 * G1 activation payload — all four fields are required together. The DB CHECK
 * constraint `chk_students_active_requires_assignment` rejects the transition to
 * ACTIVE unless every one of these is set in the same statement.
 */
export interface StudentActivateRequest {
  departmentId: number;
  courseId: number;
  registerNumber: string;
  currentSemester: number;
  section: string;
  admissionYear?: number;
}

/**
 * `PUT /api/students/{id}` payload (`StudentAdminUpdateRequest` on the backend).
 * Deliberately excludes `registerNumber` and `status`: those are set only, together,
 * by `POST /api/students/{id}/activate` and `PATCH /api/students/{id}/(de)activate`
 * (see studentService.ts) — that is what keeps the DB CHECK constraint
 * `chk_students_active_requires_assignment` satisfiable. A `null` field is left
 * unchanged (partial-update semantics), not cleared.
 */
export interface StudentUpdateRequest {
  departmentId: number | null;
  courseId: number | null;
  currentSemester: number | null;
  section: string | null;
  admissionYear?: number | null;
}

// ---------------------------------------------------------------------------------
// Faculty
// ---------------------------------------------------------------------------------

export interface FacultyResponse {
  id: number;
  userId: number;
  email: string;
  fullName: string;
  employeeCode: string;
  departmentId: number;
  departmentName: string;
  designation: string | null;
  status: FacultyStatus;
  createdAt: string;
  updatedAt: string;
}

export interface FacultyListParams extends ListParams {
  status?: FacultyStatus;
  departmentId?: number;
}

/**
 * Faculty has no PENDING state (V3 comment) — an admin provisions the account and the
 * profile together. The frontend does this as two calls against existing/new backend
 * endpoints; see services/facultyService.ts for exactly which.
 */
export interface FacultyCreateRequest {
  email: string;
  password: string;
  fullName: string;
  employeeCode: string;
  departmentId: number;
  designation?: string;
}

export interface FacultyUpdateRequest {
  employeeCode: string;
  departmentId: number;
  designation?: string;
  status: FacultyStatus;
}

// ---------------------------------------------------------------------------------
// Enrollment (read-only summary used by nothing yet in Phase 3, kept for completeness)
// ---------------------------------------------------------------------------------

export interface EnrollmentResponse {
  id: number;
  studentId: number;
  studentRegisterNumber: string | null;
  studentName: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  academicYear: string;
  semester: number;
  section: string;
  status: EnrollmentStatus;
  createdAt: string;
  updatedAt: string;
}
