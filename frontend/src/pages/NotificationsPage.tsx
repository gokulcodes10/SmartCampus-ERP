import { useMemo, useState } from "react";
import { Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { NotificationList } from "@/components/notifications/NotificationList";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useNotifications } from "@/hooks/useNotifications";
import { useServerTable } from "@/hooks/useServerTable";
import * as notificationService from "@/services/notificationService";
import type { NotificationListParams, NotificationResponse, NotificationType } from "@/types/realtime";
import { extractErrorMessage } from "@/utils/apiError";

const ALL = "__ALL__";
const TYPES: NotificationType[] = [
  "ANNOUNCEMENT",
  "PLACEMENT_UPDATE",
  "APPLICATION_UPDATE",
  "INTERVIEW_UPDATE",
  "CONTEST_UPDATE",
  "LEADERBOARD_UPDATE",
  "ATTENDANCE_WARNING",
];

const TYPE_LABELS: Record<NotificationType, string> = {
  ANNOUNCEMENT: "Announcement",
  PLACEMENT_UPDATE: "Placement",
  APPLICATION_UPDATE: "Application",
  INTERVIEW_UPDATE: "Interview",
  CONTEST_UPDATE: "Contest",
  LEADERBOARD_UPDATE: "Leaderboard",
  ATTENDANCE_WARNING: "Attendance",
};

/**
 * `/notifications` (every role) — the full notification centre: server-paged list,
 * unread-only toggle, type filter, per-row mark-read/delete, mark-all-read, and
 * clear-all behind a confirm. Always the caller's own rows (server-enforced) — there
 * is no `userId` control anywhere on this page.
 */
export default function NotificationsPage() {
  const { markAllRead: markAllReadGlobal, removeAll: removeAllGlobal, refresh: refreshGlobal } = useNotifications();

  const [unreadOnly, setUnreadOnly] = useState(false);
  const [typeFilter, setTypeFilter] = useState(ALL);

  const filters = useMemo(
    () => ({
      unreadOnly: unreadOnly || undefined,
      type: typeFilter === ALL ? undefined : (typeFilter as NotificationType),
    }),
    [unreadOnly, typeFilter],
  );

  const { data, isLoading, error, setPage, refresh } = useServerTable<
    NotificationResponse,
    Omit<NotificationListParams, "search" | "page" | "size" | "sort">
  >(notificationService.listNotifications, filters, { pageSize: 20, sort: "createdAt,desc" });

  const [isMarkingAll, setIsMarkingAll] = useState(false);
  const [rowError, setRowError] = useState<string | null>(null);
  const [clearAllOpen, setClearAllOpen] = useState(false);
  const [isClearing, setIsClearing] = useState(false);
  const [clearError, setClearError] = useState<string | null>(null);

  function refreshBoth() {
    refresh();
    refreshGlobal();
  }

  async function handleMarkRead(id: number) {
    setRowError(null);
    try {
      await notificationService.markRead(id);
      refreshBoth();
    } catch (err) {
      setRowError(extractErrorMessage(err, "Failed to mark this notification as read."));
    }
  }

  async function handleDelete(id: number) {
    setRowError(null);
    try {
      await notificationService.deleteNotification(id);
      refreshBoth();
    } catch (err) {
      setRowError(extractErrorMessage(err, "Failed to delete this notification."));
    }
  }

  async function handleMarkAllRead() {
    setIsMarkingAll(true);
    setRowError(null);
    try {
      await markAllReadGlobal();
      refresh();
    } catch (err) {
      setRowError(extractErrorMessage(err, "Failed to mark every notification as read."));
    } finally {
      setIsMarkingAll(false);
    }
  }

  async function handleClearAll() {
    setIsClearing(true);
    setClearError(null);
    try {
      await removeAllGlobal();
      setClearAllOpen(false);
      refresh();
    } catch (err) {
      setClearError(extractErrorMessage(err, "Failed to clear your notifications."));
    } finally {
      setIsClearing(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Notifications</h1>
          <p className="text-muted-foreground">Everything sent to you, newest first.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" disabled={isMarkingAll} onClick={handleMarkAllRead}>
            {isMarkingAll ? "Marking…" : "Mark all read"}
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={!data || data.totalElements === 0}
            onClick={() => {
              setClearError(null);
              setClearAllOpen(true);
            }}
          >
            <Trash2Icon />
            Clear all
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All notifications</CardTitle>
          <CardDescription>Filter by read state or type.</CardDescription>
          <div className="flex flex-wrap items-center gap-2 pt-2">
            <Button
              type="button"
              variant={unreadOnly ? "default" : "outline"}
              size="sm"
              onClick={() => setUnreadOnly((v) => !v)}
            >
              Unread only
            </Button>
            <Select value={typeFilter} onValueChange={(value) => value && setTypeFilter(value)}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="All types" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All types</SelectItem>
                {TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {TYPE_LABELS[t]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {rowError && (
            <Alert variant="destructive">
              <AlertDescription>{rowError}</AlertDescription>
            </Alert>
          )}
          <NotificationList
            notifications={data?.content ?? []}
            isLoading={isLoading}
            error={error}
            emptyMessage="No notifications match these filters."
            onMarkRead={handleMarkRead}
            onDelete={handleDelete}
          />
          {data && (
            <PaginationBar
              page={data.page}
              size={data.size}
              totalElements={data.totalElements}
              totalPages={data.totalPages}
              onPageChange={setPage}
            />
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={clearAllOpen}
        onOpenChange={setClearAllOpen}
        title="Clear all notifications?"
        description={
          clearError ? (
            <span className="text-destructive">{clearError}</span>
          ) : (
            "This permanently deletes every notification in your notification centre. This cannot be undone."
          )
        }
        confirmLabel="Clear all"
        destructive
        isConfirming={isClearing}
        onConfirm={handleClearAll}
      />
    </div>
  );
}
