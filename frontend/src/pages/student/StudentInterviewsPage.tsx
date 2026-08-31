import { useMemo, useState } from "react";
import { CalendarClockIcon, PencilIcon, PlusIcon, RefreshCwIcon } from "lucide-react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { InterviewFormDialog } from "@/components/interview/InterviewFormDialog";
import { InterviewRescheduleDialog } from "@/components/interview/InterviewRescheduleDialog";
import { InterviewStatusDialog } from "@/components/interview/InterviewStatusDialog";
import { InterviewOutcomeBadge, InterviewStatusBadge } from "@/components/interview/StatusBadge";
import {
  INTERVIEW_TYPE_LABELS,
  MODE_LABELS,
  STATUS_TRANSITIONS,
  formatDateTime,
} from "@/components/interview/interviewLabels";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useServerTable } from "@/hooks/useServerTable";
import * as interviewService from "@/services/interviewService";
import type { InterviewListParams, InterviewResponse, InterviewStatus } from "@/types/interview";

const ALL = "__ALL__";
const STATUSES: InterviewStatus[] = ["SCHEDULED", "RESCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"];

/**
 * `/student/interviews` — the caller's own interviews: list, schedule, edit,
 * reschedule and status/cancel, all against the caller's own rows (server-enforced).
 */
export default function StudentInterviewsPage() {
  const [statusFilter, setStatusFilter] = useState(ALL);

  const filters = useMemo(
    () => ({ status: statusFilter === ALL ? undefined : (statusFilter as InterviewStatus) }),
    [statusFilter],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable<
    InterviewResponse,
    Omit<InterviewListParams, "search" | "page" | "size" | "sort">
  >(interviewService.listInterviews, filters, { pageSize: 10, sort: "scheduledStart,asc" });

  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<InterviewResponse | null>(null);
  const [rescheduleTarget, setRescheduleTarget] = useState<InterviewResponse | null>(null);
  const [statusTarget, setStatusTarget] = useState<InterviewResponse | null>(null);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My interviews</h1>
          <p className="text-muted-foreground">Everything you have scheduled, past and upcoming.</p>
        </div>
        <Button onClick={() => setScheduleOpen(true)}>
          <PlusIcon />
          Schedule an interview
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All interviews</CardTitle>
          <CardDescription>Search by title or company, or filter by status.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search title or company…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-56"
            />
            <Select value={statusFilter} onValueChange={(value) => value && setStatusFilter(value)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All statuses</SelectItem>
                {STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          {isLoading && <p className="py-8 text-center text-sm text-muted-foreground">Loading…</p>}
          {!isLoading && data?.content.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">No interviews match these filters.</p>
          )}
          {!isLoading &&
            data?.content.map((interview) => {
              const canTransition = STATUS_TRANSITIONS[interview.status].length > 0;
              return (
                <div key={interview.id} className="rounded-lg border border-border p-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0 space-y-1">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="font-medium">{interview.title}</span>
                        <InterviewStatusBadge status={interview.status} />
                        {interview.outcome && <InterviewOutcomeBadge outcome={interview.outcome} />}
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {INTERVIEW_TYPE_LABELS[interview.interviewType]}
                        {interview.companyName ? ` — ${interview.companyName}` : ""}
                        {interview.roundName ? ` · ${interview.roundName}` : ""}
                      </p>
                      <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
                        <CalendarClockIcon className="size-3.5" />
                        {formatDateTime(interview.scheduledStart)} – {formatDateTime(interview.scheduledEnd)}
                        <span className="text-xs">({MODE_LABELS[interview.mode]})</span>
                      </p>
                      {interview.mode === "ONLINE" && interview.meetingLink && (
                        <a
                          href={interview.meetingLink}
                          target="_blank"
                          rel="noreferrer"
                          className="text-sm text-primary underline underline-offset-2"
                        >
                          Join meeting
                        </a>
                      )}
                      {interview.location && interview.mode !== "ONLINE" && (
                        <p className="text-sm text-muted-foreground">{interview.location}</p>
                      )}
                      {interview.cancellationReason && (
                        <p className="text-sm text-destructive">Cancelled: {interview.cancellationReason}</p>
                      )}
                      {interview.feedback && (
                        <p className="text-sm text-muted-foreground">Feedback: {interview.feedback}</p>
                      )}
                    </div>
                    <div className="flex shrink-0 flex-wrap gap-1.5">
                      <Button type="button" size="sm" variant="outline" onClick={() => setEditTarget(interview)}>
                        <PencilIcon />
                        Edit
                      </Button>
                      {canTransition && (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setRescheduleTarget(interview)}
                        >
                          <RefreshCwIcon />
                          Reschedule
                        </Button>
                      )}
                      {canTransition && (
                        <Button type="button" size="sm" onClick={() => setStatusTarget(interview)}>
                          Update status
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}

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

      <InterviewFormDialog
        open={scheduleOpen}
        onOpenChange={setScheduleOpen}
        mode="create"
        isAdmin={false}
        onSaved={() => refresh()}
      />

      <InterviewFormDialog
        open={editTarget !== null}
        onOpenChange={(open) => !open && setEditTarget(null)}
        mode="edit"
        interview={editTarget}
        isAdmin={false}
        onSaved={() => refresh()}
      />

      <InterviewRescheduleDialog
        open={rescheduleTarget !== null}
        onOpenChange={(open) => !open && setRescheduleTarget(null)}
        interview={rescheduleTarget}
        onRescheduled={() => refresh()}
      />

      <InterviewStatusDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        interview={statusTarget}
        onUpdated={() => refresh()}
      />
    </div>
  );
}
