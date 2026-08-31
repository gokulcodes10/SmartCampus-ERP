import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeftIcon } from "lucide-react";

import { ApplicationStatusBadge } from "@/components/placement/ApplicationStatusBadge";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as applicationService from "@/services/applicationService";
import * as jobService from "@/services/jobService";
import { downloadResumePdf } from "@/services/resumeService";
import { extractErrorMessage } from "@/utils/apiError";
import { ADMIN_APPLICATION_TRANSITIONS } from "@/types/placement";
import type {
  ApplicationBulkStatusResponse,
  ApplicationStatus,
  EligibleStudentRow,
  JobResponse,
  PlacementApplicationResponse,
} from "@/types/placement";

const ALL_VALUE = "all";
const APPLICATION_STATUSES: ApplicationStatus[] = [
  "APPLIED",
  "UNDER_REVIEW",
  "SHORTLISTED",
  "INTERVIEW_SCHEDULED",
  "SELECTED",
  "REJECTED",
  "WITHDRAWN",
];

type Tab = "applicants" | "eligible";

/**
 * `/admin/jobs/:jobId/applicants` — the applicants for one drive (with per-row status
 * transitions and bulk shortlist/reject), plus a second tab of everyone who COULD apply
 * (`GET /{jobId}/eligible-students`). Status controls only ever offer transitions the
 * §6 STATUS TRANSITION TABLE actually permits from the row's current status.
 */
export default function JobApplicantsPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const id = Number(jobId);

  const [job, setJob] = useState<JobResponse | null>(null);
  const [jobError, setJobError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    jobService
      .getJob(id)
      .then(setJob)
      .catch((err) => setJobError(extractErrorMessage(err, "Failed to load this drive.")));
  }, [id]);

  const [tab, setTab] = useState<Tab>("applicants");

  // --- applicants tab ---
  const [statusFilter, setStatusFilter] = useState<string>(ALL_VALUE);
  const applicantFilters = useMemo(() => {
    const f: { jobId: number; status?: ApplicationStatus } = { jobId: id };
    if (statusFilter !== ALL_VALUE) f.status = statusFilter as ApplicationStatus;
    return f;
  }, [id, statusFilter]);

  const {
    data,
    isLoading,
    error,
    setPage,
    search,
    setSearch,
    refresh,
  } = useServerTable(applicationService.listApplications, applicantFilters, { sort: "appliedAt,desc" });

  const [downloadingResumeId, setDownloadingResumeId] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  async function handleResumeDownload(resumeId: number, resumeTitle: string) {
    setDownloadError(null);
    setDownloadingResumeId(resumeId);
    try {
      await downloadResumePdf(resumeId, `${resumeTitle}.pdf`);
    } catch (err) {
      setDownloadError(extractErrorMessage(err, "Failed to download this resume."));
    } finally {
      setDownloadingResumeId(null);
    }
  }

  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    setSelectedIds(new Set());
  }, [data]);

  function toggleSelected(appId: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(appId)) next.delete(appId);
      else next.add(appId);
      return next;
    });
  }

  const selectedRows = useMemo(
    () => (data?.content ?? []).filter((row) => selectedIds.has(row.id)),
    [data, selectedIds],
  );

  function canBulk(target: ApplicationStatus): boolean {
    return selectedRows.some((row) => ADMIN_APPLICATION_TRANSITIONS[row.status].includes(target));
  }

  const [isBulking, setIsBulking] = useState(false);
  const [bulkResult, setBulkResult] = useState<ApplicationBulkStatusResponse | null>(null);
  const [bulkError, setBulkError] = useState<string | null>(null);

  async function handleBulk(target: ApplicationStatus) {
    if (selectedIds.size === 0) return;
    setIsBulking(true);
    setBulkError(null);
    setBulkResult(null);
    try {
      const result = await applicationService.bulkUpdateApplicationStatus({
        applicationIds: Array.from(selectedIds),
        status: target,
        decisionNote: null,
      });
      setBulkResult(result);
      setSelectedIds(new Set());
      refresh();
    } catch (err) {
      setBulkError(extractErrorMessage(err, "Failed to update the selected applications."));
    } finally {
      setIsBulking(false);
    }
  }

  // --- per-row status update ---
  const [statusTarget, setStatusTarget] = useState<PlacementApplicationResponse | null>(null);
  const [statusValue, setStatusValue] = useState<ApplicationStatus | "">("");
  const [statusNote, setStatusNote] = useState("");
  const [statusDialogError, setStatusDialogError] = useState<string | null>(null);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);

  function openStatusDialog(row: PlacementApplicationResponse) {
    const options = ADMIN_APPLICATION_TRANSITIONS[row.status];
    setStatusTarget(row);
    setStatusValue(options[0] ?? "");
    setStatusNote("");
    setStatusDialogError(null);
  }

  async function handleStatusSubmit(event: FormEvent) {
    event.preventDefault();
    if (!statusTarget || !statusValue) return;
    setIsUpdatingStatus(true);
    setStatusDialogError(null);
    try {
      await applicationService.updateApplicationStatus(statusTarget.id, {
        status: statusValue,
        decisionNote: statusNote.trim() || null,
      });
      setStatusTarget(null);
      refresh();
    } catch (err) {
      setStatusDialogError(extractErrorMessage(err, "Failed to update this application's status."));
    } finally {
      setIsUpdatingStatus(false);
    }
  }

  // --- eligible students tab ---
  const eligibleFetch = useMemo(
    () =>
      (params: { page: number; size: number; sort?: string; search?: string }) =>
        jobService.listEligibleStudents(id, { page: params.page, size: params.size }),
    [id],
  );
  const {
    data: eligibleData,
    isLoading: isEligibleLoading,
    error: eligibleError,
    setPage: setEligiblePage,
  } = useServerTable<EligibleStudentRow, Record<string, unknown>>(eligibleFetch, {});

  if (jobError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{jobError}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <Link
          to="/admin/jobs"
          className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeftIcon className="size-3.5" />
          Back to drives
        </Link>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight">
          {job ? job.title : "Loading…"}
          {job && <span className="ml-2 text-lg font-normal text-muted-foreground">at {job.companyName}</span>}
        </h1>
        {job && (
          <p className="text-muted-foreground">
            Deadline {new Date(job.applicationDeadline).toLocaleString()} · {job.applicationCount} application
            {job.applicationCount === 1 ? "" : "s"}
          </p>
        )}
      </div>

      <div className="flex gap-2 border-b border-border">
        <button
          type="button"
          onClick={() => setTab("applicants")}
          className={`border-b-2 px-3 py-2 text-sm font-medium ${
            tab === "applicants"
              ? "border-primary text-foreground"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Applicants
        </button>
        <button
          type="button"
          onClick={() => setTab("eligible")}
          className={`border-b-2 px-3 py-2 text-sm font-medium ${
            tab === "eligible"
              ? "border-primary text-foreground"
              : "border-transparent text-muted-foreground hover:text-foreground"
          }`}
        >
          Eligible students
        </button>
      </div>

      {tab === "applicants" && (
        <Card>
          <CardHeader>
            <CardTitle>Applicants</CardTitle>
            <CardDescription>Search by student name or register number, or filter by status.</CardDescription>
            <div className="flex flex-wrap items-center gap-2 pt-2">
              <Input
                placeholder="Search applicants…"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="max-w-sm"
              />
              <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value ?? ALL_VALUE)}>
                <SelectTrigger className="w-44">
                  <SelectValue placeholder="All statuses" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_VALUE}>All statuses</SelectItem>
                  {APPLICATION_STATUSES.map((s) => (
                    <SelectItem key={s} value={s}>
                      {s}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>

              <div className="ml-auto flex items-center gap-2">
                <span className="text-xs text-muted-foreground">{selectedIds.size} selected</span>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!canBulk("SHORTLISTED") || isBulking}
                  onClick={() => handleBulk("SHORTLISTED")}
                >
                  Shortlist selected
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={!canBulk("REJECTED") || isBulking}
                  onClick={() => handleBulk("REJECTED")}
                >
                  Reject selected
                </Button>
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
            {bulkError && (
              <div className="px-4 pb-2">
                <Alert variant="destructive">
                  <AlertDescription>{bulkError}</AlertDescription>
                </Alert>
              </div>
            )}
            {downloadError && (
              <div className="px-4 pb-2">
                <Alert variant="destructive">
                  <AlertDescription>{downloadError}</AlertDescription>
                </Alert>
              </div>
            )}
            {bulkResult && (
              <div className="px-4 pb-2">
                <Alert>
                  <AlertDescription>
                    Updated {bulkResult.updated} of {bulkResult.requested} application(s).
                    {bulkResult.skipped.length > 0 && (
                      <ul className="mt-1 list-inside list-disc text-xs">
                        {bulkResult.skipped.map((s) => (
                          <li key={s.applicationId}>
                            Application #{s.applicationId}: {s.reason}
                          </li>
                        ))}
                      </ul>
                    )}
                  </AlertDescription>
                </Alert>
              </div>
            )}

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8" />
                  <TableHead>Student</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Applied</TableHead>
                  <TableHead>Resume</TableHead>
                  <TableHead>Cover note</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading && (
                  <TableRow>
                    <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                      Loading…
                    </TableCell>
                  </TableRow>
                )}
                {!isLoading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                      No applicants found.
                    </TableCell>
                  </TableRow>
                )}
                {!isLoading &&
                  data?.content.map((row) => {
                    const options = ADMIN_APPLICATION_TRANSITIONS[row.status];
                    return (
                      <TableRow key={row.id}>
                        <TableCell>
                          <input
                            type="checkbox"
                            className="size-4 rounded border-border"
                            checked={selectedIds.has(row.id)}
                            onChange={() => toggleSelected(row.id)}
                          />
                        </TableCell>
                        <TableCell>
                          <div className="font-medium">{row.studentName}</div>
                          <div className="text-xs text-muted-foreground">{row.registerNumber ?? "—"}</div>
                        </TableCell>
                        <TableCell className="text-muted-foreground">{row.departmentName ?? "—"}</TableCell>
                        <TableCell>
                          <ApplicationStatusBadge status={row.status} />
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {new Date(row.appliedAt).toLocaleDateString()}
                        </TableCell>
                        <TableCell>
                          {row.resumeId !== null ? (
                            <Button
                              variant="link"
                              size="sm"
                              className="h-auto p-0"
                              disabled={downloadingResumeId === row.resumeId}
                              onClick={() => handleResumeDownload(row.resumeId!, row.resumeTitle ?? "resume")}
                            >
                              {downloadingResumeId === row.resumeId
                                ? "Downloading…"
                                : (row.resumeTitle ?? "Download")}
                            </Button>
                          ) : (
                            <span className="text-muted-foreground">Not attached</span>
                          )}
                        </TableCell>
                        <TableCell className="max-w-48 truncate text-muted-foreground">
                          {row.coverNote ?? "—"}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={options.length === 0}
                            onClick={() => openStatusDialog(row)}
                          >
                            Update status
                          </Button>
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
      )}

      {tab === "eligible" && (
        <Card>
          <CardHeader>
            <CardTitle>Eligible students</CardTitle>
            <CardDescription>Every student who could apply to this drive, whether they have or not.</CardDescription>
          </CardHeader>
          <CardContent className="px-0">
            {eligibleError && (
              <div className="px-4 pb-2">
                <Alert variant="destructive">
                  <AlertDescription>{eligibleError}</AlertDescription>
                </Alert>
              </div>
            )}
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Student</TableHead>
                  <TableHead>Department</TableHead>
                  <TableHead>CGPA</TableHead>
                  <TableHead>Marks %</TableHead>
                  <TableHead>Eligible</TableHead>
                  <TableHead>Applied</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isEligibleLoading && (
                  <TableRow>
                    <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                      Loading…
                    </TableCell>
                  </TableRow>
                )}
                {!isEligibleLoading && eligibleData?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                      No students found.
                    </TableCell>
                  </TableRow>
                )}
                {!isEligibleLoading &&
                  eligibleData?.content.map((row) => (
                    <TableRow key={row.studentId}>
                      <TableCell>
                        <div className="font-medium">{row.studentName}</div>
                        <div className="text-xs text-muted-foreground">{row.registerNumber ?? "—"}</div>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{row.departmentName ?? "—"}</TableCell>
                      <TableCell>{row.cgpa ?? "—"}</TableCell>
                      <TableCell>{row.marksPercentage ?? "—"}</TableCell>
                      <TableCell>
                        {row.eligible ? (
                          <Badge className="border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400">
                            Eligible
                          </Badge>
                        ) : (
                          <div className="space-y-1">
                            <Badge variant="outline">Not eligible</Badge>
                            <ul className="list-inside list-disc text-xs text-muted-foreground">
                              {row.reasons.map((r) => (
                                <li key={r.code}>{r.message}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        {row.hasApplied && row.applicationStatus ? (
                          <ApplicationStatusBadge status={row.applicationStatus} />
                        ) : (
                          <span className="text-muted-foreground">No</span>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>

            {eligibleData && (
              <PaginationBar
                page={eligibleData.page}
                size={eligibleData.size}
                totalElements={eligibleData.totalElements}
                totalPages={eligibleData.totalPages}
                onPageChange={setEligiblePage}
              />
            )}
          </CardContent>
        </Card>
      )}

      <Dialog open={statusTarget !== null} onOpenChange={(open) => !open && setStatusTarget(null)}>
        <DialogContent>
          <form onSubmit={handleStatusSubmit}>
            <DialogHeader>
              <DialogTitle>Update status</DialogTitle>
              <DialogDescription>
                {statusTarget && `${statusTarget.studentName} — currently ${statusTarget.status}.`}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {statusDialogError && (
                <Alert variant="destructive">
                  <AlertDescription>{statusDialogError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="status-new-value">New status</Label>
                <Select
                  value={statusValue}
                  onValueChange={(value) => value && setStatusValue(value as ApplicationStatus)}
                >
                  <SelectTrigger id="status-new-value" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {statusTarget &&
                      ADMIN_APPLICATION_TRANSITIONS[statusTarget.status].map((s) => (
                        <SelectItem key={s} value={s}>
                          {s}
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="status-note">Decision note (optional)</Label>
                <textarea
                  id="status-note"
                  className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={statusNote}
                  maxLength={500}
                  onChange={(event) => setStatusNote(event.target.value)}
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setStatusTarget(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isUpdatingStatus || !statusValue}>
                {isUpdatingStatus ? "Saving…" : "Update status"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
