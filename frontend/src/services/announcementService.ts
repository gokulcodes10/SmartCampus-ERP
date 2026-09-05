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
 *          -> 200 Page<AnnouncementResponse>. ADMIN or FACULTY, and the SERVER decides
 *          the scope: an ADMIN sees every announcement, a FACULTY sees only the ones
 *          they created. There is no client-supplied "mine" flag, and adding one would
 *          be security theatre — the filter is applied in AnnouncementService.manage.
 *   GET    /api/announcements/{id}                             -> 200 AnnouncementResponse (404 if not visible)
 *   POST   /api/announcements   AnnouncementCreateRequest       -> 201 AnnouncementResponse
 *   PUT    /api/announcements/{id} AnnouncementUpdateRequest    -> 200 AnnouncementResponse
 *   DELETE /api/announcements/{id}                              -> 204
 *
 * *** WHO MAY WRITE ***: ADMIN may target any audience. FACULTY may create only a
 * DEPARTMENT announcement for their OWN department, and may update or delete only the
 * announcements they created; anything else is a 403 from the service layer, not just
 * the route rule. A faculty caller may omit `departmentId` entirely on create — the
 * server fills in their own department, which is the only legal value.
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

/** ADMIN (everything) or FACULTY (own announcements only) — GET /api/announcements/manage. */
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

/** ADMIN (any audience) or FACULTY (own department only). */
export async function createAnnouncement(payload: AnnouncementCreateRequest): Promise<AnnouncementResponse> {
  const { data } = await api.post<AnnouncementResponse>(BASE, payload);
  return data;
}

/** ADMIN, or the FACULTY creator. Never sends audience/departmentId — see the FIELD-DROP TRAP note above. */
export async function updateAnnouncement(
  id: number,
  payload: AnnouncementUpdateRequest,
): Promise<AnnouncementResponse> {
  const { data } = await api.put<AnnouncementResponse>(`${BASE}/${id}`, payload);
  return data;
}

/** ADMIN, or the FACULTY creator. Cascades: withdraws it from every recipient's notification centre. */
export async function deleteAnnouncement(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
