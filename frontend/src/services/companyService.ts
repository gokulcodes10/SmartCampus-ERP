import api from "@/services/api";
import type { ListParams, Page } from "@/types/academic";
import type { CompanyCreateRequest, CompanyResponse, CompanyStatus, CompanyUpdateRequest } from "@/types/placement";

/**
 * Typed wrappers around `/api/companies`, per the Phase 8 contract:
 *
 *   POST   /api/companies          CompanyCreateRequest -> 201 CompanyResponse         ADMIN
 *   GET    /api/companies          ?status&industry&search&page&size&sort
 *          -> 200 Page<CompanyResponse>   any authenticated role.
 *          @PageableDefault(size=20, sort="name"). `search` is passed straight through
 *          (this controller reads `search`, not `q`).
 *   GET    /api/companies/{id}     -> 200 CompanyResponse   any authenticated role
 *   PUT    /api/companies/{id}     CompanyUpdateRequest -> 200 CompanyResponse         ADMIN
 *   DELETE /api/companies/{id}     -> 204; 409 when any job still references the
 *          company — surface that message via extractErrorMessage, do not swallow it.
 */

const BASE = "/api/companies";

/** The page size used when a form needs "every company" for a picker, not a table. */
export const LOOKUP_PAGE_SIZE = 200;

export interface CompanyListParams extends ListParams {
  status?: CompanyStatus;
  industry?: string;
}

export async function listCompanies(params: CompanyListParams = {}): Promise<Page<CompanyResponse>> {
  const { data } = await api.get<Page<CompanyResponse>>(BASE, { params });
  return data;
}

/** All companies, sorted by name — for populating the job-form company picker. */
export async function listAllCompanies(): Promise<CompanyResponse[]> {
  const page = await listCompanies({ page: 0, size: LOOKUP_PAGE_SIZE, sort: "name,asc" });
  return page.content;
}

export async function getCompany(id: number): Promise<CompanyResponse> {
  const { data } = await api.get<CompanyResponse>(`${BASE}/${id}`);
  return data;
}

export async function createCompany(payload: CompanyCreateRequest): Promise<CompanyResponse> {
  const { data } = await api.post<CompanyResponse>(BASE, payload);
  return data;
}

export async function updateCompany(id: number, payload: CompanyUpdateRequest): Promise<CompanyResponse> {
  const { data } = await api.put<CompanyResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteCompany(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
