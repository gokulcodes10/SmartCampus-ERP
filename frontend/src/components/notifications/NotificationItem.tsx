import { CheckIcon, Trash2Icon } from "lucide-react";
import { Link } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { NotificationPriority, NotificationResponse, NotificationType } from "@/types/realtime";

const PRIORITY_VARIANT: Record<NotificationPriority, "outline" | "secondary" | "default" | "destructive"> = {
  LOW: "outline",
  NORMAL: "secondary",
  HIGH: "default",
  URGENT: "destructive",
};

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
 * `createdAt` is a `LocalDateTime` string with no offset (see types/realtime.ts) —
 * `new Date(...)` parses it as local time, which is exactly what we want to display.
 */
function formatTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

interface NotificationItemProps {
  notification: NotificationResponse;
  onMarkRead?: (id: number) => void;
  onDelete?: (id: number) => void;
  /** Compact rendering (bell dropdown) omits the message body. */
  compact?: boolean;
}

/**
 * One notification row. A `link: null` notification (§69 — no fake functionality)
 * renders as plain, non-clickable text rather than a button that navigates nowhere.
 */
export function NotificationItem({ notification: n, onMarkRead, onDelete, compact = false }: NotificationItemProps) {
  const content = (
    <div className={cn("min-w-0 flex-1 space-y-1", !n.read && "font-medium")}>
      <div className="flex flex-wrap items-center gap-1.5">
        {!n.read && <span className="size-1.5 shrink-0 rounded-full bg-primary" aria-hidden />}
        <span className="text-sm text-foreground">{n.title}</span>
        <Badge variant={PRIORITY_VARIANT[n.priority]}>{n.priority}</Badge>
      </div>
      {!compact && <p className="text-sm font-normal whitespace-pre-wrap text-muted-foreground">{n.message}</p>}
      <div className="flex flex-wrap items-center gap-1.5 text-xs font-normal text-muted-foreground">
        <span>{TYPE_LABELS[n.type]}</span>
        <span aria-hidden>·</span>
        <span>{formatTimestamp(n.createdAt)}</span>
      </div>
    </div>
  );

  return (
    <div className={cn("flex items-start gap-2 rounded-lg border border-border p-2.5", !n.read && "bg-muted/40")}>
      {n.link ? (
        <Link to={n.link} className="min-w-0 flex-1 rounded-md outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
          {content}
        </Link>
      ) : (
        content
      )}
      {(onMarkRead || onDelete) && (
        <div className="flex shrink-0 items-center gap-1">
          {!n.read && onMarkRead && (
            <Button type="button" size="icon-xs" variant="ghost" onClick={() => onMarkRead(n.id)}>
              <CheckIcon />
              <span className="sr-only">Mark as read</span>
            </Button>
          )}
          {onDelete && (
            <Button type="button" size="icon-xs" variant="ghost" onClick={() => onDelete(n.id)}>
              <Trash2Icon />
              <span className="sr-only">Delete</span>
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
