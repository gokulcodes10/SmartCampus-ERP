import api from "@/services/api";
import type { ListParams, Page } from "@/types/academic";
import type {
  ApplicationBulkStatusRequest,
  ApplicationBulkStatusResponse,
  ApplicationResumeUpdateRequest,
  ApplicationStatus,
  ApplicationStatusUpdateRequest,
  PlacementApplicationCreateRequest,
  PlacementApplicationResponse,
} from "@/types/placement";

/**
 * Typed wrappers around `/api/applications`, per the Phase 8 contract:
 *
 *   POST  /api/applications               PlacementApplicationCreateRequest
 *         (Phase 9: gained `resumeId: number | null` — null = apply with no resume)
 *         -> 201 PlacementApplicationResponse                                 STUDENT
 *         403 when not eligible, 409 when already applied, 400 when the drive is not
 *         OPEN or the deadline passed — surface the backend message verbatim.
 *         (Phase 9: response gained `resumeId`/`resumeTitle`, both null when unattached)
 *   GET   /api/applications/me            ?status&page&size&sort
 *         -> 200 Page<PlacementApplicationResponse>                           STUDENT
 *         @PageableDefault(size=20, sort="appliedAt")
 *   GET   /api/applications               ?jobId&companyId&status&departmentId&search&
 *         page&size&sort -> 200 Page<PlacementApplicationResponse>            ADMIN
 *         `search` matches student name OR register number, passed straight through.
 *   GET   /api/applications/{id}          -> 200 PlacementApplicationResponse
 *         owner STUDENT or ADMIN; 404 (never 403) otherwise
 *   PATCH /api/applications/{id}/status   ApplicationStatusUpdateRequest -> 200        ADMIN
 *   POST  /api/applications/bulk-status   ApplicationBulkStatusRequest
 *         -> 200 ApplicationBulkStatusResponse                                ADMIN
 *   POST  /api/applications/{id}/withdraw -> 200 PlacementApplicationResponse
 *         owner STUDENT only
 *   PATCH /api/applications/{id}/resume   ApplicationResumeUpdateRequest { resumeId }
 *         -> 200 PlacementApplicationResponse                                 STUDENT
 *         owner only; 400 once the application is SELECTED/REJECTED/WITHDRAWN (Phase 9)
 */

const BASE = "/api/applications";

export interface MyApplicationListParams extends ListParams {
  status?: ApplicationStatus;
}

export interface ApplicationListParams extends ListParams {
  jobId?: number;
  companyId?: number;
  status?: ApplicationStatus;
  departmentId?: number;
}

export async function applyToJob(
  payload: PlacementApplicationCreateRequest,
): Promise<PlacementApplicationResponse> {
  const { data } = await api.post<PlacementApplicationResponse>(BASE, payload);
  return data;
}

export async function listMyApplications(
  params: MyApplicationListParams = {},
): Promise<Page<PlacementApplicationResponse>> {
  const { data } = await api.get<Page<PlacementApplicationResponse>>(`${BASE}/me`, { params });
  return data;
}

export async function listApplications(
  params: ApplicationListParams = {},
): Promise<Page<PlacementApplicationResponse>> {
  const { data } = await api.get<Page<PlacementApplicationResponse>>(BASE, { params });
  return data;
}

export async function getApplication(id: number): Promise<PlacementApplicationResponse> {
  const { data } = await api.get<PlacementApplicationResponse>(`${BASE}/${id}`);
  return data;
}

export async function updateApplicationStatus(
  id: number,
  payload: ApplicationStatusUpdateRequest,
): Promise<PlacementApplicationResponse> {
  const { data } = await api.patch<PlacementApplicationResponse>(`${BASE}/${id}/status`, payload);
  return data;
}

export async function bulkUpdateApplicationStatus(
  payload: ApplicationBulkStatusRequest,
): Promise<ApplicationBulkStatusResponse> {
  const { data } = await api.post<ApplicationBulkStatusResponse>(`${BASE}/bulk-status`, payload);
  return data;
}

export async function withdrawApplication(id: number): Promise<PlacementApplicationResponse> {
  const { data } = await api.post<PlacementApplicationResponse>(`${BASE}/${id}/withdraw`);
  return data;
}

export async function attachResumeToApplication(
  id: number,
  payload: ApplicationResumeUpdateRequest,
): Promise<PlacementApplicationResponse> {
  const { data } = await api.patch<PlacementApplicationResponse>(`${BASE}/${id}/resume`, payload);
  return data;
}
