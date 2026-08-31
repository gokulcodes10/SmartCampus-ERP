import { useEffect, useRef, useState } from "react";
import { BellIcon } from "lucide-react";
import { Link } from "react-router-dom";

import { NotificationList } from "@/components/notifications/NotificationList";
import { Button } from "@/components/ui/button";
import { useNotifications } from "@/hooks/useNotifications";

function formatBadgeCount(count: number): string {
  return count > 99 ? "99+" : String(count);
}

/**
 * Header bell: an unread badge (nothing at 0, "99+" above 99), a dropdown of the ten
 * most recent notifications, "Mark all read", and a link to the full centre. Shows a
 * small muted dot when the socket is closed so a user is never silently stale — the
 * badge count itself always comes from the server, never a local guess.
 */
export function NotificationBell() {
  const { unreadCount, recentNotifications, isLoadingRecent, recentError, connectionState, markRead, markAllRead } =
    useNotifications();
  const [open, setOpen] = useState(false);
  const [isMarkingAll, setIsMarkingAll] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  async function handleMarkAllRead() {
    setIsMarkingAll(true);
    try {
      await markAllRead();
    } finally {
      setIsMarkingAll(false);
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className="relative"
        aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : "Notifications"}
        onClick={() => setOpen((o) => !o)}
      >
        <BellIcon />
        {unreadCount > 0 && (
          <span className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] leading-none font-semibold text-destructive-foreground">
            {formatBadgeCount(unreadCount)}
          </span>
        )}
        {connectionState === "closed" && (
          <span
            className="absolute -right-0.5 -bottom-0.5 size-2 rounded-full bg-muted-foreground ring-2 ring-background"
            aria-hidden
          />
        )}
        {connectionState === "closed" && <span className="sr-only">Live updates disconnected</span>}
      </Button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-xl border border-border bg-popover p-3 text-popover-foreground shadow-lg">
          <div className="mb-2 flex items-center justify-between gap-2">
            <span className="text-sm font-semibold">Notifications</span>
            {connectionState === "closed" && (
              <span className="text-xs text-muted-foreground">Live updates paused</span>
            )}
          </div>

          <div className="max-h-80 overflow-y-auto">
            <NotificationList
              notifications={recentNotifications}
              isLoading={isLoadingRecent}
              error={recentError}
              emptyMessage="You're all caught up."
              onMarkRead={markRead}
              compact
            />
          </div>

          <div className="mt-3 flex items-center justify-between gap-2 border-t border-border pt-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={unreadCount === 0 || isMarkingAll}
              onClick={handleMarkAllRead}
            >
              {isMarkingAll ? "Marking…" : "Mark all read"}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setOpen(false)}
              render={<Link to="/notifications" />}
            >
              View all
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
