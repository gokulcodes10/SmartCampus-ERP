import { AnnouncementBoard } from "@/components/announcements/AnnouncementBoard";
import { useServerTable } from "@/hooks/useServerTable";
import * as announcementService from "@/services/announcementService";
import type { AnnouncementResponse } from "@/types/realtime";

/**
 * `/announcements` (every role) — the read-only active board from
 * `GET /api/announcements`, newest first. The backend already scopes visibility to
 * the caller's role/department (ALL + role-specific + their own DEPARTMENT
 * announcements for STUDENT/FACULTY; every audience for ADMIN) — this page has no
 * filter controls of its own to duplicate that server-side decision.
 */
export default function AnnouncementsPage() {
  const { data, isLoading, error, setPage } = useServerTable<AnnouncementResponse, Record<string, never>>(
    announcementService.listBoard,
    {},
    { pageSize: 10, sort: "publishedAt,desc" },
  );

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Announcements</h1>
        <p className="text-muted-foreground">Campus-wide and department announcements that apply to you.</p>
      </div>
      <AnnouncementBoard data={data} isLoading={isLoading} error={error} onPageChange={setPage} />
    </div>
  );
}
