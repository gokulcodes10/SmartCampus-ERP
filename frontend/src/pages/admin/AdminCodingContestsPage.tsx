import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, RefreshCwIcon, Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
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
import * as contestService from "@/services/contestService";
import * as problemService from "@/services/problemService";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";
import type {
  ContestCreateRequest,
  ContestDetailResponse,
  ContestProblemRequest,
  ContestStatus,
  ContestSummaryResponse,
  ProblemSummaryResponse,
} from "@/types/coding";

const ALL_STATUSES = "all";
const STATUSES: ContestStatus[] = ["DRAFT", "PUBLISHED", "CANCELLED"];

const FORM_FIELDS = ["slug", "title", "startTime", "endTime", "penaltyMinutesPerWrongAttempt"] as const;

interface FormState {
  slug: string;
  title: string;
  description: string;
  startTime: string;
  endTime: string;
  status: ContestStatus;
  penaltyMinutesPerWrongAttempt: string;
}

const EMPTY_FORM: FormState = {
  slug: "",
  title: "",
  description: "",
  startTime: "",
  endTime: "",
  status: "DRAFT",
  penaltyMinutesPerWrongAttempt: "10",
};

/** Converts a backend `LocalDateTime` string to the value a `datetime-local` input needs. */
function toDatetimeLocalValue(iso: string): string {
  return iso.length >= 16 ? iso.slice(0, 16) : iso;
}

/** Converts a `datetime-local` input value back to a `LocalDateTime`-shaped string. */
function fromDatetimeLocalValue(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}

const EMPTY_CONTEST_PROBLEM_FORM = { problemId: "", ordinal: "1", points: "100" };

/** `/admin/coding/contests` — contest CRUD, attaching problems, and recomputing the leaderboard. */
export default function AdminCodingContestsPage() {
  const [status, setStatus] = useState<string>(ALL_STATUSES);

  const filters = useMemo(
    () => (status === ALL_STATUSES ? {} : { status: status as ContestStatus }),
    [status],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    contestService.listContests,
    filters,
    { sort: "startTime,desc" },
  );

  // --- create / edit contest ---
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ContestSummaryResponse | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  async function openEdit(contest: ContestSummaryResponse) {
    setFormError(null);
    try {
      const detail = await contestService.getContest(contest.id);
      setEditing(contest);
      setForm({
        slug: detail.slug,
        title: detail.title,
        description: detail.description ?? "",
        startTime: toDatetimeLocalValue(detail.startTime),
        endTime: toDatetimeLocalValue(detail.endTime),
        status: detail.status,
        penaltyMinutesPerWrongAttempt: String(detail.penaltyMinutesPerWrongAttempt),
      });
      setFieldErrors({});
      setDialogOpen(true);
    } catch (err) {
      setFormError(extractErrorMessage(err, "Failed to load this contest."));
    }
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.slug.trim()) errors.slug = "Slug is required.";
    else if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(form.slug.trim())) {
      errors.slug = "Use lowercase letters, digits and hyphens only.";
    }
    if (!form.title.trim()) errors.title = "Title is required.";
    if (!form.startTime) errors.startTime = "Start time is required.";
    if (!form.endTime) errors.endTime = "End time is required.";
    if (form.startTime && form.endTime && form.endTime <= form.startTime) {
      errors.endTime = "End time must be after the start time.";
    }
    const penalty = Number(form.penaltyMinutesPerWrongAttempt);
    if (!Number.isInteger(penalty) || penalty < 0 || penalty > 1440) {
      errors.penaltyMinutesPerWrongAttempt = "Penalty must be a whole number from 0 to 1440 minutes.";
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    const payload: ContestCreateRequest = {
      slug: form.slug.trim(),
      title: form.title.trim(),
      description: form.description.trim() || null,
      startTime: fromDatetimeLocalValue(form.startTime),
      endTime: fromDatetimeLocalValue(form.endTime),
      status: form.status,
      penaltyMinutesPerWrongAttempt: Number(form.penaltyMinutesPerWrongAttempt),
    };
    setIsSaving(true);
    try {
      if (editing) {
        await contestService.updateContest(editing.id, payload);
      } else {
        await contestService.createContest(payload);
      }
      setDialogOpen(false);
      refresh();
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, FORM_FIELDS);
        setFieldErrors(parsed.fieldErrors);
        setFormError(parsed.formError);
      } else {
        setFormError(extractErrorMessage(err, "Failed to save this contest."));
      }
    } finally {
      setIsSaving(false);
    }
  }

  // --- delete contest ---
  const [deleteTarget, setDeleteTarget] = useState<ContestSummaryResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await contestService.deleteContest(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this contest."));
    } finally {
      setIsDeleting(false);
    }
  }

  // --- manage problems ---
  const [problemsContest, setProblemsContest] = useState<ContestSummaryResponse | null>(null);
  const [contestDetail, setContestDetail] = useState<ContestDetailResponse | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const [allProblems, setAllProblems] = useState<ProblemSummaryResponse[]>([]);
  const [contestProblemForm, setContestProblemForm] = useState(EMPTY_CONTEST_PROBLEM_FORM);
  const [contestProblemError, setContestProblemError] = useState<string | null>(null);
  const [isAddingProblem, setIsAddingProblem] = useState(false);
  const [removeTarget, setRemoveTarget] = useState<{ problemId: number; label: string } | null>(null);
  const [isRemoving, setIsRemoving] = useState(false);

  const [recomputeMessage, setRecomputeMessage] = useState<string | null>(null);
  const [isRecomputing, setIsRecomputing] = useState(false);

  function loadContestDetail(contestId: number) {
    setIsLoadingDetail(true);
    setDetailError(null);
    contestService
      .getContest(contestId)
      .then(setContestDetail)
      .catch((err) => setDetailError(extractErrorMessage(err, "Failed to load this contest.")))
      .finally(() => setIsLoadingDetail(false));
  }

  function openProblems(contest: ContestSummaryResponse) {
    setProblemsContest(contest);
    setContestDetail(null);
    setContestProblemForm({ ...EMPTY_CONTEST_PROBLEM_FORM });
    setContestProblemError(null);
    setRecomputeMessage(null);
    loadContestDetail(contest.id);
  }

  useEffect(() => {
    if (problemsContest === null) return;
    problemService
      .listProblems({ page: 0, size: 100, sort: "title,asc" })
      .then((page) => setAllProblems(page.content))
      .catch(() => setAllProblems([]));
  }, [problemsContest]);

  async function handleAddProblem(event: FormEvent) {
    event.preventDefault();
    if (!problemsContest) return;
    setContestProblemError(null);

    const problemId = Number(contestProblemForm.problemId);
    const ordinal = Number(contestProblemForm.ordinal);
    const points = Number(contestProblemForm.points);
    if (!problemId) {
      setContestProblemError("Choose a problem to attach.");
      return;
    }
    if (!Number.isInteger(ordinal) || ordinal <= 0) {
      setContestProblemError("Ordinal must be a positive whole number.");
      return;
    }
    if (!Number.isInteger(points) || points <= 0) {
      setContestProblemError("Points must be a positive whole number.");
      return;
    }

    const payload: ContestProblemRequest = { problemId, ordinal, points };
    setIsAddingProblem(true);
    try {
      await contestService.addContestProblem(problemsContest.id, payload);
      loadContestDetail(problemsContest.id);
      setContestProblemForm({
        problemId: "",
        ordinal: String(ordinal + 1),
        points: "100",
      });
      refresh();
    } catch (err) {
      setContestProblemError(extractErrorMessage(err, "Failed to attach this problem."));
    } finally {
      setIsAddingProblem(false);
    }
  }

  async function handleRemoveProblem() {
    if (!problemsContest || !removeTarget) return;
    setIsRemoving(true);
    try {
      await contestService.removeContestProblem(problemsContest.id, removeTarget.problemId);
      setRemoveTarget(null);
      loadContestDetail(problemsContest.id);
      refresh();
    } catch (err) {
      setDetailError(extractErrorMessage(err, "Failed to remove this problem."));
    } finally {
      setIsRemoving(false);
    }
  }

  async function handleRecompute() {
    if (!problemsContest) return;
    setIsRecomputing(true);
    setRecomputeMessage(null);
    try {
      const detail = await contestService.recomputeContestLeaderboard(problemsContest.id);
      setContestDetail(detail);
      setRecomputeMessage("Leaderboard recomputed from submissions.");
    } catch (err) {
      setRecomputeMessage(extractErrorMessage(err, "Failed to recompute the leaderboard."));
    } finally {
      setIsRecomputing(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Coding contests</h1>
          <p className="text-muted-foreground">Create contests, attach problems, and recompute standings.</p>
        </div>
        <Button onClick={openCreate}>
          <PlusIcon />
          Add contest
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All contests</CardTitle>
          <CardDescription>Search by title, or filter by authoring status.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search contests…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={status} onValueChange={(value) => setStatus(value ?? ALL_STATUSES)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
                {STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s.charAt(0) + s.slice(1).toLowerCase()}
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

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Phase</TableHead>
                <TableHead>Starts</TableHead>
                <TableHead>Ends</TableHead>
                <TableHead>Problems</TableHead>
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
                    No contests found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((contest) => (
                  <TableRow key={contest.id}>
                    <TableCell className="font-medium">{contest.title}</TableCell>
                    <TableCell>
                      <Badge variant={contest.status === "PUBLISHED" ? "default" : "outline"}>
                        {contest.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{contest.phase}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(contest.startTime).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(contest.endTime).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openProblems(contest)}>
                        {contest.problemCount} problem{contest.problemCount === 1 ? "" : "s"}
                      </Button>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(contest)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(contest);
                          }}
                        >
                          <Trash2Icon />
                          <span className="sr-only">Delete</span>
                        </Button>
                      </div>
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

      {/* --- create / edit contest dialog --- */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit contest" : "Add contest"}</DialogTitle>
              <DialogDescription>
                {editing ? `Update ${editing.title}.` : "Create a new coding contest."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="contest-slug">Slug</Label>
                  <Input
                    id="contest-slug"
                    value={form.slug}
                    onChange={(event) => setForm((f) => ({ ...f, slug: event.target.value }))}
                    aria-invalid={!!fieldErrors.slug}
                    placeholder="fall-sprint-2026"
                  />
                  {fieldErrors.slug && <p className="text-xs text-destructive">{fieldErrors.slug}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="contest-title">Title</Label>
                  <Input
                    id="contest-title"
                    value={form.title}
                    maxLength={200}
                    onChange={(event) => setForm((f) => ({ ...f, title: event.target.value }))}
                    aria-invalid={!!fieldErrors.title}
                  />
                  {fieldErrors.title && <p className="text-xs text-destructive">{fieldErrors.title}</p>}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="contest-description">Description</Label>
                <textarea
                  id="contest-description"
                  className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.description}
                  onChange={(event) => setForm((f) => ({ ...f, description: event.target.value }))}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="contest-start">Start time</Label>
                  <Input
                    id="contest-start"
                    type="datetime-local"
                    value={form.startTime}
                    onChange={(event) => setForm((f) => ({ ...f, startTime: event.target.value }))}
                    aria-invalid={!!fieldErrors.startTime}
                  />
                  {fieldErrors.startTime && <p className="text-xs text-destructive">{fieldErrors.startTime}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="contest-end">End time</Label>
                  <Input
                    id="contest-end"
                    type="datetime-local"
                    value={form.endTime}
                    onChange={(event) => setForm((f) => ({ ...f, endTime: event.target.value }))}
                    aria-invalid={!!fieldErrors.endTime}
                  />
                  {fieldErrors.endTime && <p className="text-xs text-destructive">{fieldErrors.endTime}</p>}
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="contest-status">Status</Label>
                  <Select
                    value={form.status}
                    onValueChange={(value) => value && setForm((f) => ({ ...f, status: value as ContestStatus }))}
                  >
                    <SelectTrigger id="contest-status" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {STATUSES.map((s) => (
                        <SelectItem key={s} value={s}>
                          {s.charAt(0) + s.slice(1).toLowerCase()}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="contest-penalty">Penalty (min / wrong attempt)</Label>
                  <Input
                    id="contest-penalty"
                    type="number"
                    min={0}
                    max={1440}
                    value={form.penaltyMinutesPerWrongAttempt}
                    onChange={(event) =>
                      setForm((f) => ({ ...f, penaltyMinutesPerWrongAttempt: event.target.value }))
                    }
                    aria-invalid={!!fieldErrors.penaltyMinutesPerWrongAttempt}
                  />
                  {fieldErrors.penaltyMinutesPerWrongAttempt && (
                    <p className="text-xs text-destructive">{fieldErrors.penaltyMinutesPerWrongAttempt}</p>
                  )}
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create contest"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete contest?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes <strong>{deleteTarget?.title}</strong>, its attached problems and its
                participants.
              </>
            )}
          </>
        }
        confirmLabel="Delete"
        destructive
        isConfirming={isDeleting}
        onConfirm={handleDelete}
      />

      {/* --- manage problems dialog --- */}
      <Dialog open={problemsContest !== null} onOpenChange={(open) => !open && setProblemsContest(null)}>
        <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Problems — {problemsContest?.title}</DialogTitle>
            <DialogDescription>Attach problems with an A/B/C ordinal and a point value.</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            {detailError && (
              <Alert variant="destructive">
                <AlertDescription>{detailError}</AlertDescription>
              </Alert>
            )}

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">#</TableHead>
                  <TableHead>Problem</TableHead>
                  <TableHead>Points</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoadingDetail && (
                  <TableRow>
                    <TableCell colSpan={4} className="py-6 text-center text-muted-foreground">
                      Loading…
                    </TableCell>
                  </TableRow>
                )}
                {!isLoadingDetail && contestDetail?.problems.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="py-6 text-center text-muted-foreground">
                      No problems attached yet.
                    </TableCell>
                  </TableRow>
                )}
                {!isLoadingDetail &&
                  contestDetail?.problems.map((cp) => (
                    <TableRow key={cp.id}>
                      <TableCell className="font-mono">{cp.label}</TableCell>
                      <TableCell>{cp.problemTitle}</TableCell>
                      <TableCell>{cp.points}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => setRemoveTarget({ problemId: cp.problemId, label: cp.problemTitle })}
                        >
                          <Trash2Icon />
                          <span className="sr-only">Remove</span>
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>

            <form onSubmit={handleAddProblem} className="space-y-3 rounded-lg border border-border p-3">
              <p className="text-sm font-medium">Attach a problem</p>
              {contestProblemError && (
                <Alert variant="destructive">
                  <AlertDescription>{contestProblemError}</AlertDescription>
                </Alert>
              )}
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="space-y-1.5 sm:col-span-1">
                  <Label htmlFor="cp-problem">Problem</Label>
                  <Select
                    value={contestProblemForm.problemId}
                    onValueChange={(value) =>
                      value && setContestProblemForm((f) => ({ ...f, problemId: value }))
                    }
                  >
                    <SelectTrigger id="cp-problem" className="w-full">
                      <SelectValue placeholder="Select a problem" />
                    </SelectTrigger>
                    <SelectContent>
                      {allProblems.map((p) => (
                        <SelectItem key={p.id} value={String(p.id)}>
                          {p.title}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="cp-ordinal">Ordinal</Label>
                  <Input
                    id="cp-ordinal"
                    type="number"
                    min={1}
                    value={contestProblemForm.ordinal}
                    onChange={(event) =>
                      setContestProblemForm((f) => ({ ...f, ordinal: event.target.value }))
                    }
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="cp-points">Points</Label>
                  <Input
                    id="cp-points"
                    type="number"
                    min={1}
                    value={contestProblemForm.points}
                    onChange={(event) =>
                      setContestProblemForm((f) => ({ ...f, points: event.target.value }))
                    }
                  />
                </div>
              </div>
              <div className="flex justify-end">
                <Button type="submit" disabled={isAddingProblem}>
                  {isAddingProblem ? "Attaching…" : "Attach"}
                </Button>
              </div>
            </form>

            <div className="flex items-center justify-between rounded-lg border border-border p-3">
              <div>
                <p className="text-sm font-medium">Recompute leaderboard</p>
                <p className="text-xs text-muted-foreground">
                  Rebuilds every participant's score from the submissions that justify it.
                </p>
                {recomputeMessage && <p className="mt-1 text-xs text-muted-foreground">{recomputeMessage}</p>}
              </div>
              <Button type="button" variant="outline" onClick={handleRecompute} disabled={isRecomputing}>
                <RefreshCwIcon className={isRecomputing ? "animate-spin" : undefined} />
                {isRecomputing ? "Recomputing…" : "Recompute"}
              </Button>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setProblemsContest(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={removeTarget !== null}
        onOpenChange={(open) => !open && setRemoveTarget(null)}
        title="Remove problem from contest?"
        description={`This detaches "${removeTarget?.label ?? ""}" from the contest. Existing submissions for it are kept.`}
        confirmLabel="Remove"
        destructive
        isConfirming={isRemoving}
        onConfirm={handleRemoveProblem}
      />
    </div>
  );
}
