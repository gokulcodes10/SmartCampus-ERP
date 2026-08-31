import { AxiosError } from "axios";

import api from "@/services/api";
import type { Page } from "@/types/academic";
import type {
  ResumeDuplicateRequest,
  ResumePrefillResponse,
  ResumeResponse,
  ResumeSaveRequest,
  ResumeSummaryResponse,
} from "@/types/resume";

/**
 * Typed wrappers around `/api/resumes`, per the Phase 9 contract:
 *
 *   POST   /api/resumes                 ResumeSaveRequest      -> 201 ResumeResponse
 *   GET    /api/resumes/me              ?page&size&sort        -> 200 Page<ResumeSummaryResponse>
 *          default sort updatedAt,desc
 *   GET    /api/resumes/prefill                                -> 200 ResumePrefillResponse
 *   GET    /api/resumes/{id}                                   -> 200 ResumeResponse
 *          owner STUDENT or ADMIN; 404 (never 403) otherwise
 *   PUT    /api/resumes/{id}            ResumeSaveRequest      -> 200 ResumeResponse
 *          owner STUDENT; 409 "RESUME_LOCKED" once attached to an application
 *   DELETE /api/resumes/{id}                                   -> 204; 409 when locked
 *   POST   /api/resumes/{id}/duplicate  {title}                -> 201 ResumeResponse
 *   GET    /api/resumes/{id}/pdf                                -> 200 application/pdf (binary)
 *
 * Errors use the §47 envelope. A 409 with `error: "RESUME_LOCKED"` means the resume is
 * attached to a placement application and is permanently read-only — the student must
 * duplicate it to keep editing.
 */

const BASE = "/api/resumes";

export interface ResumeListParams {
  page?: number;
  size?: number;
  sort?: string;
}

export async function createResume(payload: ResumeSaveRequest): Promise<ResumeResponse> {
  const { data } = await api.post<ResumeResponse>(BASE, payload);
  return data;
}

export async function listMyResumes(params: ResumeListParams = {}): Promise<Page<ResumeSummaryResponse>> {
  const { data } = await api.get<Page<ResumeSummaryResponse>>(`${BASE}/me`, { params });
  return data;
}

export async function getResume(id: number): Promise<ResumeResponse> {
  const { data } = await api.get<ResumeResponse>(`${BASE}/${id}`);
  return data;
}

export async function updateResume(id: number, payload: ResumeSaveRequest): Promise<ResumeResponse> {
  const { data } = await api.put<ResumeResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteResume(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}

export async function duplicateResume(
  id: number,
  payload: ResumeDuplicateRequest,
): Promise<ResumeResponse> {
  const { data } = await api.post<ResumeResponse>(`${BASE}/${id}/duplicate`, payload);
  return data;
}

export async function getResumePrefill(): Promise<ResumePrefillResponse> {
  const { data } = await api.get<ResumePrefillResponse>(`${BASE}/prefill`);
  return data;
}

/**
 * `responseType: "blob"` makes axios deliver an ERROR body as a Blob too (it has no
 * way to know in advance whether a failed response is JSON or binary), so a naive
 * `extractErrorMessage(err)` would see an opaque Blob with no `.message` and fall back
 * to a useless generic string. Detect that shape here, read the Blob as text, and
 * re-parse it as the real §47 envelope before rethrowing a normal `Error` carrying the
 * backend's actual message — every caller of this function gets a real error message.
 */
export async function fetchResumePdf(id: number): Promise<Blob> {
  try {
    const { data } = await api.get<Blob>(`${BASE}/${id}/pdf`, { responseType: "blob" });
    return data;
  } catch (err) {
    if (err instanceof AxiosError && err.response?.data instanceof Blob) {
      const message = await extractMessageFromErrorBlob(err.response.data);
      if (message) {
        throw new Error(message);
      }
    }
    throw err;
  }
}

/** Best-effort parse of a §47 envelope out of the Blob axios hands back for a failed
 *  `responseType: "blob"` request. Returns null (never throws) for anything that isn't
 *  that shape, so the caller can fall back to rethrowing the original axios error. */
async function extractMessageFromErrorBlob(blob: Blob): Promise<string | null> {
  if (!blob.type.includes("json")) return null;
  try {
    const text = await blob.text();
    const parsed = JSON.parse(text) as { message?: string };
    return parsed.message ?? null;
  } catch {
    return null;
  }
}

/** Fetches the PDF and drives a real browser download — no `<a download>` server-side link exists. */
export async function downloadResumePdf(id: number, fileName: string): Promise<void> {
  const blob = await fetchResumePdf(id);
  const url = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
  } finally {
    URL.revokeObjectURL(url);
  }
}
