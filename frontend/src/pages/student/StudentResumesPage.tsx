import { useState } from "react";
import { Link } from "react-router-dom";
import { FileTextIcon, LockIcon, PlusIcon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { DuplicateResumeDialog } from "@/components/resume/DuplicateResumeDialog";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as resumeService from "@/services/resumeService";
import { extractErrorMessage } from "@/utils/apiError";
import { RESUME_TEMPLATE_LABELS } from "@/types/resume";
import type { ResumeSummaryResponse } from "@/types/resume";

const EMPTY_FILTERS = {};

/**
 * `/student/resumes` — the student's own saved resume versions (`GET /api/resumes/me`),
 * newest-updated first. A LOCKED resume was attached to a placement application and is
 * permanently read-only from here on; the only way to keep working on it is to
 * duplicate it into a fresh, unlocked version.
 */
export default function StudentResumesPage() {
  const { data, isLoading, error, setPage, refresh } = useServerTable(
    resumeService.listMyResumes,
    EMPTY_FILTERS,
    { sort: "updatedAt,desc" },
  );

  const [deleteTarget, setDeleteTarget] = useState<ResumeSummaryResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [duplicateTarget, setDuplicateTarget] = useState<ResumeSummaryResponse | null>(null);
  const [isDuplicating, setIsDuplicating] = useState(false);
  const [duplicateError, setDuplicateError] = useState<string | null>(null);

  const [downloadingId, setDownloadingId] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await resumeService.deleteResume(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this resume."));
    } finally {
      setIsDeleting(false);
    }
  }

  async function handleDuplicate(title: string) {
    if (!duplicateTarget) return;
    setIsDuplicating(true);
    setDuplicateError(null);
    try {
      await resumeService.duplicateResume(duplicateTarget.id, { title });
      setDuplicateTarget(null);
      refresh();
    } catch (err) {
      setDuplicateError(extractErrorMessage(err, "Failed to duplicate this resume."));
    } finally {
      setIsDuplicating(false);
    }
  }

  async function handleDownload(resume: ResumeSummaryResponse) {
    setDownloadError(null);
    setDownloadingId(resume.id);
    try {
      const fileName = `resume-${resume.title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "") || "download"}-${resume.id}.pdf`;
      await resumeService.downloadResumePdf(resume.id, fileName);
    } catch (err) {
      setDownloadError(extractErrorMessage(err, "Failed to download the PDF."));
    } finally {
      setDownloadingId(null);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My resumes</h1>
          <p className="text-muted-foreground">Build and manage resume versions to attach to your applications.</p>
        </div>
        <Button render={<Link to="/student/resumes/new" />}>
          <PlusIcon />
          New resume
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Saved versions</CardTitle>
          <CardDescription>Sorted by most recently updated.</CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          {(error || downloadError) && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{error ?? downloadError}</AlertDescription>
              </Alert>
            </div>
          )}

          {!isLoading && data?.content.length === 0 && (
            <div className="flex flex-col items-center gap-3 px-4 py-12 text-center">
              <FileTextIcon className="size-8 text-muted-foreground" />
              <div>
                <p className="font-medium">You haven&rsquo;t built a resume yet</p>
                <p className="max-w-sm text-sm text-muted-foreground">
                  A resume is what you attach to a placement application — build one now so it&rsquo;s ready
                  when you apply to a drive.
                </p>
              </div>
              <Button render={<Link to="/student/resumes/new" />}>
                <PlusIcon />
                New resume
              </Button>
            </div>
          )}

          {(isLoading || (data?.content.length ?? 0) > 0) && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Template</TableHead>
                  <TableHead>Updated</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading && (
                  <TableRow>
                    <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                      Loading…
                    </TableCell>
                  </TableRow>
                )}
                {!isLoading &&
                  data?.content.map((resume) => (
                    <TableRow key={resume.id}>
                      <TableCell className="font-medium">
                        <div className="flex items-center gap-2">
                          {resume.locked ? (
                            <span title="Attached to a placement application — duplicate it to keep editing">
                              {resume.title}
                            </span>
                          ) : (
                            <Link to={`/student/resumes/${resume.id}`} className="hover:underline">
                              {resume.title}
                            </Link>
                          )}
                          {resume.locked && (
                            <Badge
                              variant="outline"
                              className="gap-1"
                              title="Attached to a placement application — duplicate it to keep editing"
                            >
                              <LockIcon className="size-3" />
                              Locked
                            </Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{RESUME_TEMPLATE_LABELS[resume.template]}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {new Date(resume.updatedAt).toLocaleString()}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1.5">
                          {resume.locked ? (
                            <Button
                              variant="outline"
                              size="sm"
                              disabled
                              title="Locked — attached to a placement application. Duplicate it to keep editing."
                            >
                              Edit
                            </Button>
                          ) : (
                            <Button
                              variant="outline"
                              size="sm"
                              render={<Link to={`/student/resumes/${resume.id}`} />}
                            >
                              Edit
                            </Button>
                          )}
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              setDuplicateError(null);
                              setDuplicateTarget(resume);
                            }}
                          >
                            Duplicate
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={downloadingId === resume.id}
                            onClick={() => handleDownload(resume)}
                          >
                            {downloadingId === resume.id ? "Preparing…" : "Download PDF"}
                          </Button>
                          {!resume.locked && (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="text-destructive"
                              onClick={() => {
                                setDeleteError(null);
                                setDeleteTarget(resume);
                              }}
                            >
                              Delete
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          )}

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
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete resume?"
        description={
          deleteError ? (
            <span className="text-destructive">{deleteError}</span>
          ) : (
            <>
              This permanently deletes <strong>{deleteTarget?.title}</strong>. This cannot be undone.
            </>
          )
        }
        confirmLabel="Delete"
        destructive
        isConfirming={isDeleting}
        onConfirm={handleDelete}
      />

      <DuplicateResumeDialog
        open={duplicateTarget !== null}
        onOpenChange={(open) => !open && setDuplicateTarget(null)}
        sourceTitle={duplicateTarget?.title ?? ""}
        onConfirm={handleDuplicate}
        isSubmitting={isDuplicating}
        error={duplicateError}
      />
    </div>
  );
}
