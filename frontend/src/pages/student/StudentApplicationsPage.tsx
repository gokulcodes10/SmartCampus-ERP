import { useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { ApplicationStatusBadge } from "@/components/placement/ApplicationStatusBadge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as applicationService from "@/services/applicationService";
import { downloadResumePdf } from "@/services/resumeService";
import { extractErrorMessage } from "@/utils/apiError";
import { WITHDRAWABLE_APPLICATION_STATUSES } from "@/types/placement";
import type { ApplicationStatus, PlacementApplicationResponse } from "@/types/placement";

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

/**
 * `/student/applications` — the caller's own placement applications
 * (`GET /api/applications/me`). Withdraw is offered only for non-terminal statuses
 * (APPLIED, UNDER_REVIEW, SHORTLISTED, INTERVIEW_SCHEDULED) — SELECTED, REJECTED and
 * WITHDRAWN are all terminal and the backend has no transition out of them.
 */
export default function StudentApplicationsPage() {
  const [statusFilter, setStatusFilter] = useState<string>(ALL_VALUE);

  const filters = useMemo(() => {
    const f: { status?: ApplicationStatus } = {};
    if (statusFilter !== ALL_VALUE) f.status = statusFilter as ApplicationStatus;
    return f;
  }, [statusFilter]);

  const { data, isLoading, error, setPage, refresh } = useServerTable(
    applicationService.listMyApplications,
    filters,
    { sort: "appliedAt,desc" },
  );

  const [withdrawTarget, setWithdrawTarget] = useState<PlacementApplicationResponse | null>(null);
  const [isWithdrawing, setIsWithdrawing] = useState(false);
  const [withdrawError, setWithdrawError] = useState<string | null>(null);

  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  async function handleDownload(resumeId: number, resumeTitle: string) {
    setDownloadError(null);
    setDownloadingId(resumeId);
    try {
      await downloadResumePdf(resumeId, `${resumeTitle}.pdf`);
    } catch (err) {
      setDownloadError(extractErrorMessage(err, "Failed to download this resume."));
    } finally {
      setDownloadingId(null);
    }
  }

  async function handleWithdraw() {
    if (!withdrawTarget) return;
    setIsWithdrawing(true);
    setWithdrawError(null);
    try {
      await applicationService.withdrawApplication(withdrawTarget.id);
      setWithdrawTarget(null);
      refresh();
    } catch (err) {
      setWithdrawError(extractErrorMessage(err, "Failed to withdraw this application."));
    } finally {
      setIsWithdrawing(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My applications</h1>
        <p className="text-muted-foreground">Placement drives you've applied to.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Applications</CardTitle>
          <CardDescription>Filter by status.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value ?? ALL_VALUE)}>
              <SelectTrigger className="w-48">
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
          {downloadError && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{downloadError}</AlertDescription>
              </Alert>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Drive</TableHead>
                <TableHead>Company</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Applied</TableHead>
                <TableHead>Resume</TableHead>
                <TableHead>Decision note</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    You haven&rsquo;t applied to any drives yet.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((app) => (
                  <TableRow key={app.id}>
                    <TableCell className="font-medium">
                      <Link to={`/student/jobs/${app.jobId}`} className="hover:underline">
                        {app.jobTitle}
                      </Link>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{app.companyName}</TableCell>
                    <TableCell>
                      <ApplicationStatusBadge status={app.status} />
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(app.appliedAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell>
                      {app.resumeId !== null ? (
                        <Button
                          variant="link"
                          size="sm"
                          className="h-auto p-0"
                          disabled={downloadingId === app.resumeId}
                          onClick={() => handleDownload(app.resumeId!, app.resumeTitle ?? "resume")}
                        >
                          {downloadingId === app.resumeId ? "Downloading…" : (app.resumeTitle ?? "Download")}
                        </Button>
                      ) : (
                        <span className="text-muted-foreground">None</span>
                      )}
                    </TableCell>
                    <TableCell className="max-w-56 truncate text-muted-foreground">
                      {app.decisionNote ?? "—"}
                    </TableCell>
                    <TableCell className="text-right">
                      {WITHDRAWABLE_APPLICATION_STATUSES.includes(app.status) && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => {
                            setWithdrawError(null);
                            setWithdrawTarget(app);
                          }}
                        >
                          Withdraw
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
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

      <ConfirmDialog
        open={withdrawTarget !== null}
        onOpenChange={(open) => !open && setWithdrawTarget(null)}
        title="Withdraw application?"
        description={
          <>
            {withdrawError ? (
              <span className="text-destructive">{withdrawError}</span>
            ) : (
              <>
                This permanently withdraws your application to{" "}
                <strong>{withdrawTarget?.jobTitle}</strong> at {withdrawTarget?.companyName}.{" "}
                <strong>This cannot be undone, and you will not be able to re-apply to this drive.</strong>
              </>
            )}
          </>
        }
        confirmLabel="Withdraw"
        destructive
        isConfirming={isWithdrawing}
        onConfirm={handleWithdraw}
      />
    </div>
  );
}
