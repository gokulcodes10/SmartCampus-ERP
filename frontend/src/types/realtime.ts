/**
 * Types for the Phase 11 (Real-Time) API: notifications, announcements, and the raw
 * WebSocket notification stream. Mirrors the PHASE 11 IMPLEMENTATION CONTRACT
 * (§5, §10, §12) field-for-field — every response type is **flat**, matching the
 * convention documented at the top of `types/academic.ts`: a referenced entity is
 * always an `xId`/`xName` scalar pair, never a nested object.
 *
 * All timestamps are `LocalDateTime` strings with no offset, e.g.
 * `"2026-08-31T10:15:30.123"` — typed as `string`, never `Date`. `new Date(...)` on
 * such a string parses as local time; do not append a trailing "Z" and do not assume
 * UTC when formatting one for display.
 *
 * NOT independently verified against a live backend response at authoring time (this
 * agent owns FRONTEND only, built in parallel with the Phase 11 backend agents) — see
 * the build report for exactly what was and was not re-checked once the backend booted.
 */

import type { ListParams, Page } from "@/types/academic";

export type { Page };

// ---------------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------------

export type NotificationType =
  | "ANNOUNCEMENT"
  | "PLACEMENT_UPDATE"
  | "APPLICATION_UPDATE"
  | "INTERVIEW_UPDATE"
  | "CONTEST_UPDATE"
  | "LEADERBOARD_UPDATE"
  | "ATTENDANCE_WARNING";

export type NotificationPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";

export type NotificationReferenceType =
  | "ANNOUNCEMENT"
  | "JOB"
  | "PLACEMENT_APPLICATION"
  | "INTERVIEW"
  | "CONTEST"
  | "SUBJECT";

export type AnnouncementAudience = "ALL" | "STUDENTS" | "FACULTY" | "DEPARTMENT";

export type RealtimeEventType = "READY" | "NOTIFICATION" | "UNREAD_COUNT" | "PONG";

// ---------------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------------

export interface NotificationResponse {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  priority: NotificationPriority;
  /** Nullable — a null link renders as a non-clickable row, never a dead button (§69). */
  link: string | null;
  referenceType: NotificationReferenceType | null;
  referenceId: number | null;
  /** Non-null iff type === "ANNOUNCEMENT". */
  announcementId: number | null;
  /** === (readAt !== null). There is no separate `isRead` field server-side. */
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export interface MarkAllReadResponse {
  markedCount: number;
  unreadCount: number;
}

export interface DeleteAllNotificationsResponse {
  deletedCount: number;
}

/**
 * Query params for `GET /api/notifications`. The endpoint has no free-text search —
 * `search` is declared only so this fits `useServerTable`'s `fetchPage` signature;
 * `notificationService.listNotifications` deliberately drops it before the request
 * goes out rather than forwarding an unsupported param.
 */
export interface NotificationListParams extends ListParams {
  unreadOnly?: boolean;
  type?: NotificationType;
}

export type NotificationPage = Page<NotificationResponse>;

// ---------------------------------------------------------------------------------
// Announcements
// ---------------------------------------------------------------------------------

export interface AnnouncementResponse {
  id: number;
  title: string;
  body: string;
  audience: AnnouncementAudience;
  /** Non-null iff audience === "DEPARTMENT". */
  departmentId: number | null;
  departmentName: string | null;
  priority: NotificationPriority;
  publishedAt: string;
  /** null == never expires. */
  expiresAt: string | null;
  /** publishedAt <= now && (expiresAt == null || expiresAt > now), computed server-side. */
  active: boolean;
  /** Nullable — the author's account may since have been deleted (ON DELETE SET NULL). */
  createdById: number | null;
  createdByName: string | null;
  /** NULLABLE. Populated only for ADMIN callers (the manage endpoint); null everywhere else. */
  recipientCount: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface AnnouncementCreateRequest {
  title: string;
  body: string;
  audience: AnnouncementAudience;
  /** Required iff audience === "DEPARTMENT", else must be omitted/null. */
  departmentId?: number | null;
  /** Omit for the server default (NORMAL). */
  priority?: NotificationPriority;
  /** null == never expires; must be strictly after the publish time. */
  expiresAt?: string | null;
}

/**
 * Deliberately carries NO `audience` and NO `departmentId` — the fan-out already
 * happened at create time; re-targeting would strand notifications with the wrong
 * recipients. Re-targeting = delete + recreate. `announcementService.updateAnnouncement`
 * and the admin edit form must never send those two fields — the backend DTO does not
 * declare them and Jackson would silently drop them (a §69 button that does nothing).
 */
export interface AnnouncementUpdateRequest {
  title: string;
  body: string;
  priority?: NotificationPriority;
  expiresAt?: string | null;
}

/** Query params for the public `GET /api/announcements` board — no filters, no search. */
export interface AnnouncementBoardParams {
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Query params for `GET /api/announcements/manage` (ADMIN or FACULTY; the server scopes
 * a faculty caller to their own announcements). `search` maps to the backend's `q` —
 * see the trap note in `announcementService.ts`.
 */
export interface AnnouncementManageParams extends AnnouncementBoardParams {
  search?: string;
  audience?: AnnouncementAudience;
  includeExpired?: boolean;
}

export type AnnouncementPage = Page<AnnouncementResponse>;

// ---------------------------------------------------------------------------------
// WebSocket wire protocol
// ---------------------------------------------------------------------------------

/** The ONLY server -> client shape, over `ws(s)://.../ws/notifications?token=...`. */
export interface RealtimeEnvelope {
  event: RealtimeEventType;
  /** Present only on NOTIFICATION; null otherwise. */
  notification: NotificationResponse | null;
  /** Present on every event except PONG. */
  unreadCount: number | null;
}
