import { useEffect, useMemo, useState } from "react";
import { CalendarClockIcon, PencilIcon, PlusIcon, RefreshCwIcon, Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { InterviewFormDialog } from "@/components/interview/InterviewFormDialog";
import { InterviewRescheduleDialog } from "@/components/interview/InterviewRescheduleDialog";
import { InterviewStatusDialog } from "@/components/interview/InterviewStatusDialog";
import { InterviewOutcomeBadge, InterviewStatusBadge } from "@/components/interview/StatusBadge";
import {
  INTERVIEW_TYPE_LABELS,
  MODE_LABELS,
  STATUS_TRANSITIONS,
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from "@/components/interview/interviewLabels";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as interviewService from "@/services/interviewService";
import * as studentService from "@/services/studentService";
import type { StudentResponse } from "@/types/academic";
import type { InterviewListParams, InterviewResponse, InterviewStatus } from "@/types/interview";
import { extractErrorMessage } from "@/utils/apiError";

const ALL = "__ALL__";
const STATUSES: InterviewStatus[] = ["SCHEDULED", "RESCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"];

/**
 * `/admin/interviews` — every student's interviews: filter by status and a scheduled
 * date range, schedule an interview for any student, transition status, delete.
 */
export default function AdminInterviewsPage() {
  const [statusFilter, setStatusFilter] = useState(ALL);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");

  const filters = useMemo(
    () => ({
      status: statusFilter === ALL ? undefined : (statusFilter as InterviewStatus),
      from: from ? fromDatetimeLocalValue(from) : undefined,
      to: to ? fromDatetimeLocalValue(to) : undefined,
    }),
    [statusFilter, from, to],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable<
    InterviewResponse,
    Omit<InterviewListParams, "search" | "page" | "size" | "sort">
  >(interviewService.listInterviews, filters, { pageSize: 15, sort: "scheduledStart,asc" });

  // Students, for the "schedule for a student" picker.
  const [students, setStudents] = useState<StudentResponse[]>([]);
  const [studentsError, setStudentsError] = useState<string | null>(null);

  useEffect(() => {
    studentService
      .listStudents({ status: "ACTIVE", size: 200 })
      .then((result) => setStudents(result.content))
      .catch((err) => setStudentsError(extractErrorMessage(err, "Failed to load students.")));
  }, []);

  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<InterviewResponse | null>(null);
  const [rescheduleTarget, setRescheduleTarget] = useState<InterviewResponse | null>(null);
  const [statusTarget, setStatusTarget] = useState<InterviewResponse | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<InterviewResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await interviewService.deleteInterview(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this interview."));
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Interviews</h1>
          <p className="text-muted-foreground">Every scheduled interview, across every student.</p>
        </div>
        <Button onClick={() => setScheduleOpen(true)} disabled={students.length === 0}>
          <PlusIcon />
          Schedule an interview
        </Button>
      </div>

      {studentsError && (
        <Alert variant="destructive">
          <AlertDescription>{studentsError}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>All interviews</CardTitle>
          <CardDescription>Search by title or company, filter by status or scheduled date range.</CardDescription>
          <div className="flex flex-wrap items-end gap-2 pt-2">
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
            <div className="space-y-1">
              <Label htmlFor="af-from" className="text-xs text-muted-foreground">
                From
              </Label>
              <Input id="af-from" type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="af-to" className="text-xs text-muted-foreground">
                To
              </Label>
              <Input id="af-to" type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} />
            </div>
          </div>
        </CardHeader>
        <CardContent className="px-0">
          {error && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Student</TableHead>
                <TableHead>Title</TableHead>
                <TableHead>When</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                    No interviews match these filters.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((interview) => {
                  const canTransition = STATUS_TRANSITIONS[interview.status].length > 0;
                  return (
                    <TableRow key={interview.id}>
                      <TableCell>
                        <div className="font-medium">{interview.studentName}</div>
                        <div className="text-xs text-muted-foreground">
                          {interview.studentRegisterNumber ?? `#${interview.studentId}`}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div>{interview.title}</div>
                        <div className="text-xs text-muted-foreground">
                          {INTERVIEW_TYPE_LABELS[interview.interviewType]}
                          {interview.companyName ? ` — ${interview.companyName}` : ""} ·{" "}
                          {MODE_LABELS[interview.mode]}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1 text-sm">
                          <CalendarClockIcon className="size-3.5 text-muted-foreground" />
                          {toDatetimeLocalValue(interview.scheduledStart).replace("T", " ")}
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-col gap-1">
                          <InterviewStatusBadge status={interview.status} />
                          {interview.outcome && <InterviewOutcomeBadge outcome={interview.outcome} />}
                        </div>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" onClick={() => setEditTarget(interview)}>
                            <PencilIcon />
                            <span className="sr-only">Edit</span>
                          </Button>
                          {canTransition && (
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => setRescheduleTarget(interview)}
                            >
                              <RefreshCwIcon />
                              <span className="sr-only">Reschedule</span>
                            </Button>
                          )}
                          {canTransition && (
                            <Button variant="outline" size="xs" onClick={() => setStatusTarget(interview)}>
                              Status
                            </Button>
                          )}
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => {
                              setDeleteError(null);
                              setDeleteTarget(interview);
                            }}
                          >
                            <Trash2Icon />
                            <span className="sr-only">Delete</span>
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
            </TableBody>
          </Table>

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
        isAdmin
        students={students}
        studentsError={studentsError}
        onSaved={() => refresh()}
      />

      <InterviewFormDialog
        open={editTarget !== null}
        onOpenChange={(open) => !open && setEditTarget(null)}
        mode="edit"
        interview={editTarget}
        isAdmin
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

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete this interview?"
        description={
          deleteError ? (
            <span className="text-destructive">{deleteError}</span>
          ) : (
            <>
              This permanently deletes "{deleteTarget?.title}" for {deleteTarget?.studentName}.
            </>
          )
        }
        confirmLabel="Delete"
        destructive
        isConfirming={isDeleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
