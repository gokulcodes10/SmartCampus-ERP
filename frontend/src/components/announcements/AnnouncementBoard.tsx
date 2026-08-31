import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { AnnouncementPage, NotificationPriority } from "@/types/realtime";

const PRIORITY_VARIANT: Record<NotificationPriority, "outline" | "secondary" | "default" | "destructive"> = {
  LOW: "outline",
  NORMAL: "secondary",
  HIGH: "default",
  URGENT: "destructive",
};

/**
 * `publishedAt`/`expiresAt` are `LocalDateTime` strings with no offset — parsed as
 * local time, matching every other timestamp in this codebase.
 */
function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

interface AnnouncementBoardProps {
  data: AnnouncementPage | null;
  isLoading: boolean;
  error: string | null;
  onPageChange: (page: number) => void;
  /** Render `recipientCount` when present — only ever non-null for an ADMIN caller. */
  showRecipientCount?: boolean;
  emptyMessage?: string;
}

/**
 * The read-only announcement board — newest first, priority visually distinguished
 * with both a badge and a left accent bar (never color alone). Shared by the public
 * `/announcements` board; the admin manage screen uses its own table instead.
 */
export function AnnouncementBoard({
  data,
  isLoading,
  error,
  onPageChange,
  showRecipientCount = false,
  emptyMessage = "No announcements right now.",
}: AnnouncementBoardProps) {
  return (
    <div className="space-y-3">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      {isLoading && <p className="py-8 text-center text-sm text-muted-foreground">Loading…</p>}
      {!isLoading && data?.content.length === 0 && (
        <p className="py-8 text-center text-sm text-muted-foreground">{emptyMessage}</p>
      )}
      {!isLoading &&
        data?.content.map((a) => (
          <Card
            key={a.id}
            className={cn(
              "border-l-4",
              a.priority === "URGENT" && "border-l-destructive",
              a.priority === "HIGH" && "border-l-primary",
              a.priority === "NORMAL" && "border-l-border",
              a.priority === "LOW" && "border-l-border",
            )}
          >
            <CardHeader>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <CardTitle>{a.title}</CardTitle>
                  <CardDescription>
                    {formatDateTime(a.publishedAt)}
                    {a.createdByName ? ` · ${a.createdByName}` : ""}
                    {a.departmentName ? ` · ${a.departmentName}` : ""}
                  </CardDescription>
                </div>
                <div className="flex shrink-0 items-center gap-1.5">
                  <Badge variant={PRIORITY_VARIANT[a.priority]}>{a.priority}</Badge>
                  <Badge variant="outline">{a.audience}</Badge>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-2">
              <p className="text-sm whitespace-pre-wrap text-foreground">{a.body}</p>
              {(a.expiresAt || (showRecipientCount && a.recipientCount !== null)) && (
                <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                  {a.expiresAt && <span>Expires {formatDateTime(a.expiresAt)}</span>}
                  {showRecipientCount && a.recipientCount !== null && (
                    <span>
                      {a.recipientCount} recipient{a.recipientCount === 1 ? "" : "s"}
                    </span>
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      {data && (
        <PaginationBar
          page={data.page}
          size={data.size}
          totalElements={data.totalElements}
          totalPages={data.totalPages}
          onPageChange={onPageChange}
        />
      )}
    </div>
  );
}
