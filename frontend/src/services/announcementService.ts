import api from "@/services/api";
import type {
  AnnouncementBoardParams,
  AnnouncementCreateRequest,
  AnnouncementManageParams,
  AnnouncementPage,
  AnnouncementResponse,
  AnnouncementUpdateRequest,
} from "@/types/realtime";

/**
 * Typed wrappers around `/api/announcements`, per the Phase 11 implementation
 * contract (§9):
 *
 *   GET    /api/announcements?page&size&sort                 -> 200 Page<AnnouncementResponse>
 *          Active board scoped to the caller's role/department, server-side. Any role.
 *   GET    /api/announcements/manage?audience&includeExpired&q&page&size&sort
 *          -> 200 Page<AnnouncementResponse>. ADMIN only — every announcement, not just active ones.
 *   GET    /api/announcements/{id}                             -> 200 AnnouncementResponse (404 if not visible)
 *   POST   /api/announcements   AnnouncementCreateRequest       -> 201 AnnouncementResponse (ADMIN)
 *   PUT    /api/announcements/{id} AnnouncementUpdateRequest    -> 200 AnnouncementResponse (ADMIN)
 *   DELETE /api/announcements/{id}                              -> 204                      (ADMIN)
 *
 * *** QUERY-PARAMETER TRAP (the same one interviewService.ts documents) ***:
 * `useServerTable` always sends the search box's value as `search`; the manage
 * endpoint reads `q`. That mapping happens here, in `listManaged`, not in the caller —
 * a mismatch would silently return an unfiltered page with no error anywhere.
 *
 * *** FIELD-DROP TRAP ***: `AnnouncementUpdateRequest` deliberately carries no
 * `audience` and no `departmentId` — the fan-out already happened at create time.
 * Re-targeting is delete + recreate, not an edit. Sending those fields anyway would be
 * silently dropped by Jackson (a §69 button that does nothing), so the type itself
 * omits them; the admin edit form renders audience/department as read-only text.
 */

const BASE = "/api/announcements";

/** Public active board — GET /api/announcements. No filters, no search. */
export async function listBoard(params: AnnouncementBoardParams = {}): Promise<AnnouncementPage> {
  const { data } = await api.get<AnnouncementPage>(BASE, { params });
  return data;
}

/** ADMIN only — GET /api/announcements/manage. */
export async function listManaged(params: AnnouncementManageParams = {}): Promise<AnnouncementPage> {
  const { search, ...rest } = params;
  const { data } = await api.get<AnnouncementPage>(`${BASE}/manage`, {
    params: { ...rest, q: search || undefined },
  });
  return data;
}

export async function getAnnouncement(id: number): Promise<AnnouncementResponse> {
  const { data } = await api.get<AnnouncementResponse>(`${BASE}/${id}`);
  return data;
}

/** ADMIN only. */
export async function createAnnouncement(payload: AnnouncementCreateRequest): Promise<AnnouncementResponse> {
  const { data } = await api.post<AnnouncementResponse>(BASE, payload);
  return data;
}

/** ADMIN only. Never sends audience/departmentId — see the FIELD-DROP TRAP note above. */
export async function updateAnnouncement(
  id: number,
  payload: AnnouncementUpdateRequest,
): Promise<AnnouncementResponse> {
  const { data } = await api.put<AnnouncementResponse>(`${BASE}/${id}`, payload);
  return data;
}

/** ADMIN only. Cascades: withdraws the announcement from every recipient's notification centre. */
export async function deleteAnnouncement(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
