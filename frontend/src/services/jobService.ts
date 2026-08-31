import api from "@/services/api";
import type { ListParams, Page } from "@/types/academic";
import type {
  EligibleStudentRow,
  JobCreateRequest,
  JobEligibilityResponse,
  JobResponse,
  JobStatus,
  JobStatusUpdateRequest,
  JobType,
  JobUpdateRequest,
} from "@/types/placement";

/**
 * Typed wrappers around `/api/jobs`, per the Phase 8 contract. The eligibility and
 * eligible-students endpoints live on a separate `PlacementEligibilityController`
 * backend-side but share the `/api/jobs` path prefix, and this codebase has no
 * separate `eligibilityService.ts` file, so they're grouped here.
 *
 *   POST   /api/jobs                      JobCreateRequest -> 201 JobResponse          ADMIN
 *   GET    /api/jobs                      ?companyId&jobType&status&departmentId&
 *          search&acceptingOnly&page&size&sort -> 200 Page<JobResponse>  any auth role.
 *          @PageableDefault(size=20, sort="applicationDeadline"). For a non-ADMIN
 *          caller the backend FORCES the status filter to {OPEN, CLOSED} regardless of
 *          what is requested — passing DRAFT/CANCELLED as a non-admin yields an empty
 *          page, not a 403, so this module never needs to hide those options itself.
 *   GET    /api/jobs/{id}                 -> 200 JobResponse (404 for a non-admin on a
 *          DRAFT/CANCELLED job — same 404-never-403 convention as everywhere else)
 *   PUT    /api/jobs/{id}                 JobUpdateRequest -> 200 JobResponse          ADMIN
 *          (no companyId, no status field — see JobUpdateRequest doc in types/placement.ts)
 *   PATCH  /api/jobs/{id}/status           JobStatusUpdateRequest -> 200 JobResponse   ADMIN
 *   DELETE /api/jobs/{id}                 -> 204; 409 when any application references
 *          it — surface via extractErrorMessage, do not swallow it.                    ADMIN
 *   GET    /api/jobs/{jobId}/eligibility   ?studentId (ADMIN-only param; omit for self)
 *          -> 200 JobEligibilityResponse
 *   GET    /api/jobs/{jobId}/eligible-students ?page&size -> 200 Page<EligibleStudentRow>
 *          @PageableDefault(size=20).                                                  ADMIN
 */

const BASE = "/api/jobs";

export interface JobListParams extends ListParams {
  companyId?: number;
  jobType?: JobType;
  status?: JobStatus;
  departmentId?: number;
  acceptingOnly?: boolean;
}

export async function listJobs(params: JobListParams = {}): Promise<Page<JobResponse>> {
  const { data } = await api.get<Page<JobResponse>>(BASE, { params });
  return data;
}

export async function getJob(id: number): Promise<JobResponse> {
  const { data } = await api.get<JobResponse>(`${BASE}/${id}`);
  return data;
}

export async function createJob(payload: JobCreateRequest): Promise<JobResponse> {
  const { data } = await api.post<JobResponse>(BASE, payload);
  return data;
}

export async function updateJob(id: number, payload: JobUpdateRequest): Promise<JobResponse> {
  const { data } = await api.put<JobResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function updateJobStatus(id: number, payload: JobStatusUpdateRequest): Promise<JobResponse> {
  const { data } = await api.patch<JobResponse>(`${BASE}/${id}/status`, payload);
  return data;
}

export async function deleteJob(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}

/** `studentId` is an ADMIN-only param; omit it entirely for a student checking their own eligibility. */
export async function getJobEligibility(jobId: number, studentId?: number): Promise<JobEligibilityResponse> {
  const { data } = await api.get<JobEligibilityResponse>(`${BASE}/${jobId}/eligibility`, {
    params: studentId ? { studentId } : undefined,
  });
  return data;
}

export interface EligibleStudentsParams {
  page?: number;
  size?: number;
}

export async function listEligibleStudents(
  jobId: number,
  params: EligibleStudentsParams = {},
): Promise<Page<EligibleStudentRow>> {
  const { data } = await api.get<Page<EligibleStudentRow>>(`${BASE}/${jobId}/eligible-students`, { params });
  return data;
}
