import { createContext, useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";

import * as notificationService from "@/services/notificationService";
import { NotificationSocket } from "@/services/notificationSocket";
import type { SocketConnectionState } from "@/services/notificationSocket";
import { useAuth } from "@/hooks/useAuth";
import { extractErrorMessage } from "@/utils/apiError";
import { getStoredToken } from "@/utils/tokenStorage";
import type { NotificationResponse } from "@/types/realtime";

/** Size of the "most recent" list this context keeps live — matches the bell's "ten most recent". */
const RECENT_LIMIT = 10;

export interface NotificationContextValue {
  unreadCount: number;
  recentNotifications: NotificationResponse[];
  isLoadingRecent: boolean;
  recentError: string | null;
  connectionState: SocketConnectionState;
  /** Re-fetches both the unread count and the recent list from the server. */
  refresh: () => void;
  markRead: (id: number) => Promise<void>;
  markAllRead: () => Promise<void>;
  remove: (id: number) => Promise<void>;
  removeAll: () => Promise<void>;
}

export const NotificationContext = createContext<NotificationContextValue | undefined>(undefined);

/**
 * Owns the live notification stream for the whole app: the unread badge count, the
 * most recent page of notifications (for `NotificationBell`), the socket's connection
 * state, and the mutating actions every screen shares (mark read, mark all read,
 * delete, delete all).
 *
 * The server's number is always authoritative for `unreadCount` — this never
 * increments it locally on receipt of a NOTIFICATION frame, it only ever *sets* it
 * from whatever the frame (or a REST response) says. Live notifications are
 * deduplicated by id when prepended, since a REST refresh can race a socket frame.
 *
 * Must be mounted inside `<AuthProvider>` (it reads `useAuth`) and wrap the routed
 * tree so every page — not just the notification screens — can show the bell.
 */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuth();

  const [unreadCount, setUnreadCount] = useState(0);
  const [recentNotifications, setRecentNotifications] = useState<NotificationResponse[]>([]);
  const [isLoadingRecent, setIsLoadingRecent] = useState(false);
  const [recentError, setRecentError] = useState<string | null>(null);
  const [connectionState, setConnectionState] = useState<SocketConnectionState>("closed");

  const socketRef = useRef<NotificationSocket | null>(null);

  const loadRecent = useCallback(() => {
    setIsLoadingRecent(true);
    setRecentError(null);
    notificationService
      .listNotifications({ page: 0, size: RECENT_LIMIT, sort: "createdAt,desc" })
      .then((result) => setRecentNotifications(result.content))
      .catch((err) => setRecentError(extractErrorMessage(err, "Failed to load notifications.")))
      .finally(() => setIsLoadingRecent(false));
  }, []);

  const loadUnreadCount = useCallback(() => {
    notificationService
      .getUnreadCount()
      .then((result) => setUnreadCount(result.unreadCount))
      .catch(() => {
        // Best-effort — the socket's READY/UNREAD_COUNT/NOTIFICATION frames are the
        // primary source of truth for this number while connected.
      });
  }, []);

  const refresh = useCallback(() => {
    loadRecent();
    loadUnreadCount();
  }, [loadRecent, loadUnreadCount]);

  // Socket lifecycle: one connection per authenticated user. Closed on logout, on
  // unmount, and replaced (via this effect re-running) whenever the user changes — a
  // socket carrying the previous user's token after a re-login would otherwise show
  // the wrong person's notifications.
  useEffect(() => {
    if (!isAuthenticated || !user) {
      socketRef.current?.close();
      socketRef.current = null;
      setConnectionState("closed");
      setUnreadCount(0);
      setRecentNotifications([]);
      return;
    }

    const token = getStoredToken();
    if (!token) {
      setConnectionState("closed");
      return;
    }

    const socket = new NotificationSocket(token, {
      onStateChange: setConnectionState,
      onEnvelope: (envelope) => {
        if (envelope.unreadCount !== null) {
          setUnreadCount(envelope.unreadCount);
        }
        if (envelope.event === "NOTIFICATION" && envelope.notification) {
          const incoming = envelope.notification;
          setRecentNotifications((prev) => {
            if (prev.some((n) => n.id === incoming.id)) return prev;
            return [incoming, ...prev].slice(0, RECENT_LIMIT);
          });
        }
      },
    });
    socketRef.current = socket;
    socket.connect();

    loadRecent();
    loadUnreadCount();

    return () => {
      socket.close();
      if (socketRef.current === socket) {
        socketRef.current = null;
      }
    };
    // Re-run only when identity changes — loadRecent/loadUnreadCount are stable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, user?.id]);

  const markRead = useCallback(async (id: number) => {
    const updated = await notificationService.markRead(id);
    setRecentNotifications((prev) => prev.map((n) => (n.id === id ? updated : n)));
    loadUnreadCount();
  }, [loadUnreadCount]);

  const markAllRead = useCallback(async () => {
    const result = await notificationService.markAllRead();
    setUnreadCount(result.unreadCount);
    // Re-fetch rather than fabricate a client-side readAt — the server's timestamp is
    // the only authoritative one, and this codebase never invents one locally.
    loadRecent();
  }, [loadRecent]);

  const remove = useCallback(async (id: number) => {
    await notificationService.deleteNotification(id);
    setRecentNotifications((prev) => prev.filter((n) => n.id !== id));
    loadUnreadCount();
  }, [loadUnreadCount]);

  const removeAll = useCallback(async () => {
    await notificationService.deleteAllNotifications();
    setRecentNotifications([]);
    setUnreadCount(0);
  }, []);

  const value = useMemo<NotificationContextValue>(
    () => ({
      unreadCount,
      recentNotifications,
      isLoadingRecent,
      recentError,
      connectionState,
      refresh,
      markRead,
      markAllRead,
      remove,
      removeAll,
    }),
    [
      unreadCount,
      recentNotifications,
      isLoadingRecent,
      recentError,
      connectionState,
      refresh,
      markRead,
      markAllRead,
      remove,
      removeAll,
    ],
  );

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
}
