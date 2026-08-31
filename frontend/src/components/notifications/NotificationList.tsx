import { NotificationItem } from "@/components/notifications/NotificationItem";
import type { NotificationResponse } from "@/types/realtime";

interface NotificationListProps {
  notifications: NotificationResponse[];
  isLoading?: boolean;
  error?: string | null;
  emptyMessage?: string;
  onMarkRead?: (id: number) => void;
  onDelete?: (id: number) => void;
  compact?: boolean;
}

/** Renders an honest loading/error/empty state, then one `NotificationItem` per row. */
export function NotificationList({
  notifications,
  isLoading = false,
  error = null,
  emptyMessage = "No notifications.",
  onMarkRead,
  onDelete,
  compact = false,
}: NotificationListProps) {
  if (error) {
    return <p className="py-6 text-center text-sm text-destructive">{error}</p>;
  }
  if (isLoading) {
    return <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>;
  }
  if (notifications.length === 0) {
    return <p className="py-6 text-center text-sm text-muted-foreground">{emptyMessage}</p>;
  }
  return (
    <div className="space-y-2">
      {notifications.map((n) => (
        <NotificationItem key={n.id} notification={n} onMarkRead={onMarkRead} onDelete={onDelete} compact={compact} />
      ))}
    </div>
  );
}
