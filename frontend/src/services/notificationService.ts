import api from "@/services/api";
import type {
  DeleteAllNotificationsResponse,
  MarkAllReadResponse,
  NotificationListParams,
  NotificationPage,
  NotificationResponse,
  UnreadCountResponse,
} from "@/types/realtime";

/**
 * Typed wrappers around `/api/notifications`, per the Phase 11 implementation
 * contract (§9) — always the caller's own rows; there is no `userId` parameter
 * anywhere on this resource.
 *
 *   GET    /api/notifications?unreadOnly&type&page&size&sort -> 200 Page<NotificationResponse>
 *   GET    /api/notifications/unread-count                    -> 200 UnreadCountResponse
 *   PUT    /api/notifications/read-all                        -> 200 MarkAllReadResponse
 *   PUT    /api/notifications/{id}/read                       -> 200 NotificationResponse (404 if not owned)
 *   DELETE /api/notifications/{id}                             -> 204                     (404 if not owned)
 *   DELETE /api/notifications                                  -> 200 DeleteAllNotificationsResponse
 *
 * *** NO-SEARCH TRAP ***: `useServerTable` always sends the search box's value as
 * `search`, but this resource has no free-text search endpoint. Unlike
 * `interviewService.listInterviews` (which remaps `search` -> `q`), `listNotifications`
 * below simply drops it before the request goes out — sending an unsupported param
 * silently returning an unfiltered page is exactly the kind of trap this build has
 * already shipped once (Phase 3's `search`-vs-`q` mismatch).
 */

const BASE = "/api/notifications";

export async function listNotifications(params: NotificationListParams = {}): Promise<NotificationPage> {
  const { search: _search, ...rest } = params;
  const { data } = await api.get<NotificationPage>(BASE, { params: rest });
  return data;
}

export async function getUnreadCount(): Promise<UnreadCountResponse> {
  const { data } = await api.get<UnreadCountResponse>(`${BASE}/unread-count`);
  return data;
}

export async function markAllRead(): Promise<MarkAllReadResponse> {
  const { data } = await api.put<MarkAllReadResponse>(`${BASE}/read-all`);
  return data;
}

export async function markRead(id: number): Promise<NotificationResponse> {
  const { data } = await api.put<NotificationResponse>(`${BASE}/${id}/read`);
  return data;
}

export async function deleteNotification(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}

export async function deleteAllNotifications(): Promise<DeleteAllNotificationsResponse> {
  const { data } = await api.delete<DeleteAllNotificationsResponse>(BASE);
  return data;
}
