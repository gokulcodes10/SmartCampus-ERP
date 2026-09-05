import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { DifficultyBadge } from "@/components/coding/DifficultyBadge";
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
import * as problemService from "@/services/problemService";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";
import type {
  ProblemDifficulty,
  ProblemCreateRequest,
  ProblemSummaryResponse,
  TestCaseRequest,
  TestCaseResponse,
} from "@/types/coding";

const ALL_DIFFICULTIES = "all";
const ALL_PUBLISHED = "all";
const DIFFICULTIES: ProblemDifficulty[] = ["EASY", "MEDIUM", "HARD"];

const FORM_FIELDS = [
  "slug",
  "title",
  "description",
  "difficulty",
  "timeLimitMs",
  "memoryLimitKb",
] as const;

interface FormState {
  slug: string;
  title: string;
  description: string;
  inputFormat: string;
  outputFormat: string;
  constraintsText: string;
  sampleInput: string;
  sampleOutput: string;
  difficulty: ProblemDifficulty;
  timeLimitMs: string;
  memoryLimitKb: string;
  tags: string;
  published: boolean;
}

const EMPTY_FORM: FormState = {
  slug: "",
  title: "",
  description: "",
  inputFormat: "",
  outputFormat: "",
  constraintsText: "",
  sampleInput: "",
  sampleOutput: "",
  difficulty: "EASY",
  timeLimitMs: "2000",
  memoryLimitKb: "262144",
  tags: "",
  published: false,
};

const EMPTY_TEST_CASE_FORM = { ordinal: "1", input: "", expectedOutput: "", isSample: true, weight: "1" };

/** `/admin/coding/problems` — full CRUD for coding problems, plus their test cases. Copies SubjectsPage's shape. */
export default function AdminCodingProblemsPage() {
  const [difficulty, setDifficulty] = useState<string>(ALL_DIFFICULTIES);
  const [publishedFilter, setPublishedFilter] = useState<string>(ALL_PUBLISHED);

  const filters = useMemo(() => {
    const f: { difficulty?: ProblemDifficulty; published?: boolean } = {};
    if (difficulty !== ALL_DIFFICULTIES) f.difficulty = difficulty as ProblemDifficulty;
    if (publishedFilter !== ALL_PUBLISHED) f.published = publishedFilter === "true";
    return f;
  }, [difficulty, publishedFilter]);

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    problemService.listProblems,
    filters,
    { sort: "id,desc" },
  );

  // --- create / edit problem ---
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ProblemSummaryResponse | null>(null);
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

  async function openEdit(problem: ProblemSummaryResponse) {
    setFormError(null);
    try {
      const detail = await problemService.getProblem(problem.id);
      setEditing(problem);
      setForm({
        slug: detail.slug,
        title: detail.title,
        description: detail.description,
        inputFormat: detail.inputFormat ?? "",
        outputFormat: detail.outputFormat ?? "",
        constraintsText: detail.constraintsText ?? "",
        sampleInput: detail.sampleInput ?? "",
        sampleOutput: detail.sampleOutput ?? "",
        difficulty: detail.difficulty,
        timeLimitMs: String(detail.timeLimitMs),
        memoryLimitKb: String(detail.memoryLimitKb),
        tags: detail.tags.join(", "),
        published: detail.published,
      });
      setFieldErrors({});
      setDialogOpen(true);
    } catch (err) {
      setFormError(extractErrorMessage(err, "Failed to load this problem."));
    }
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.slug.trim()) errors.slug = "Slug is required.";
    else if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(form.slug.trim())) {
      errors.slug = "Use lowercase letters, digits and hyphens only (e.g. two-sum).";
    }
    if (!form.title.trim()) errors.title = "Title is required.";
    if (!form.description.trim()) errors.description = "Description is required.";

    const timeLimitMs = Number(form.timeLimitMs);
    if (!Number.isInteger(timeLimitMs) || timeLimitMs < 100 || timeLimitMs > 15000) {
      errors.timeLimitMs = "Time limit must be a whole number from 100 to 15000 ms.";
    }
    const memoryLimitKb = Number(form.memoryLimitKb);
    if (!Number.isInteger(memoryLimitKb) || memoryLimitKb < 16384 || memoryLimitKb > 512000) {
      errors.memoryLimitKb = "Memory limit must be a whole number from 16384 to 512000 KB.";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    const payload: ProblemCreateRequest = {
      slug: form.slug.trim(),
      title: form.title.trim(),
      description: form.description.trim(),
      inputFormat: form.inputFormat.trim() || null,
      outputFormat: form.outputFormat.trim() || null,
      constraintsText: form.constraintsText.trim() || null,
      sampleInput: form.sampleInput.trim() || null,
      sampleOutput: form.sampleOutput.trim() || null,
      difficulty: form.difficulty,
      timeLimitMs: Number(form.timeLimitMs),
      memoryLimitKb: Number(form.memoryLimitKb),
      tags: form.tags
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean),
      published: form.published,
    };
    setIsSaving(true);
    try {
      if (editing) {
        await problemService.updateProblem(editing.id, payload);
      } else {
        await problemService.createProblem(payload);
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
        setFormError(extractErrorMessage(err, "Failed to save this problem."));
      }
    } finally {
      setIsSaving(false);
    }
  }

  // --- delete problem ---
  const [deleteTarget, setDeleteTarget] = useState<ProblemSummaryResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await problemService.deleteProblem(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(
        extractErrorMessage(err, "Failed to delete this problem. It may already have submissions or be used in a contest."),
      );
    } finally {
      setIsDeleting(false);
    }
  }

  // --- test cases ---
  const [testCaseProblem, setTestCaseProblem] = useState<ProblemSummaryResponse | null>(null);
  const [testCases, setTestCases] = useState<TestCaseResponse[]>([]);
  const [testCasesError, setTestCasesError] = useState<string | null>(null);
  const [isLoadingTestCases, setIsLoadingTestCases] = useState(false);

  const [testCaseEditing, setTestCaseEditing] = useState<TestCaseResponse | null>(null);
  const [testCaseForm, setTestCaseForm] = useState(EMPTY_TEST_CASE_FORM);
  const [testCaseFormError, setTestCaseFormError] = useState<string | null>(null);
  const [isSavingTestCase, setIsSavingTestCase] = useState(false);
  const [deleteTestCaseTarget, setDeleteTestCaseTarget] = useState<TestCaseResponse | null>(null);
  const [isDeletingTestCase, setIsDeletingTestCase] = useState(false);

  function loadTestCases(problemId: number) {
    setIsLoadingTestCases(true);
    setTestCasesError(null);
    problemService
      .listTestCases(problemId)
      .then(setTestCases)
      .catch((err) => setTestCasesError(extractErrorMessage(err, "Failed to load test cases.")))
      .finally(() => setIsLoadingTestCases(false));
  }

  function openTestCases(problem: ProblemSummaryResponse) {
    setTestCaseProblem(problem);
    setTestCaseEditing(null);
    setTestCaseForm({
      ...EMPTY_TEST_CASE_FORM,
      ordinal: String((problem.sampleTestCaseCount + problem.hiddenTestCaseCount || 0) + 1),
    });
    setTestCaseFormError(null);
    loadTestCases(problem.id);
  }

  function startEditTestCase(tc: TestCaseResponse) {
    setTestCaseEditing(tc);
    setTestCaseForm({
      ordinal: String(tc.ordinal),
      input: tc.input,
      expectedOutput: tc.expectedOutput,
      isSample: tc.isSample,
      weight: String(tc.weight),
    });
    setTestCaseFormError(null);
  }

  function resetTestCaseForm() {
    setTestCaseEditing(null);
    setTestCaseForm({
      ...EMPTY_TEST_CASE_FORM,
      ordinal: String(testCases.length + 1),
    });
    setTestCaseFormError(null);
  }

  async function handleSaveTestCase(event: FormEvent) {
    event.preventDefault();
    if (!testCaseProblem) return;
    setTestCaseFormError(null);

    const ordinal = Number(testCaseForm.ordinal);
    const weight = Number(testCaseForm.weight);
    if (!Number.isInteger(ordinal) || ordinal <= 0) {
      setTestCaseFormError("Ordinal must be a positive whole number.");
      return;
    }
    if (!Number.isInteger(weight) || weight <= 0) {
      setTestCaseFormError("Weight must be a positive whole number.");
      return;
    }

    const payload: TestCaseRequest = {
      ordinal,
      input: testCaseForm.input,
      expectedOutput: testCaseForm.expectedOutput,
      isSample: testCaseForm.isSample,
      weight,
    };

    setIsSavingTestCase(true);
    try {
      if (testCaseEditing) {
        await problemService.updateTestCase(testCaseProblem.id, testCaseEditing.id, payload);
      } else {
        await problemService.createTestCase(testCaseProblem.id, payload);
      }
      loadTestCases(testCaseProblem.id);
      resetTestCaseForm();
      refresh();
    } catch (err) {
      setTestCaseFormError(extractErrorMessage(err, "Failed to save this test case."));
    } finally {
      setIsSavingTestCase(false);
    }
  }

  async function handleDeleteTestCase() {
    if (!testCaseProblem || !deleteTestCaseTarget) return;
    setIsDeletingTestCase(true);
    try {
      await problemService.deleteTestCase(testCaseProblem.id, deleteTestCaseTarget.id);
      setDeleteTestCaseTarget(null);
      loadTestCases(testCaseProblem.id);
      refresh();
    } catch (err) {
      setTestCasesError(extractErrorMessage(err, "Failed to delete this test case."));
    } finally {
      setIsDeletingTestCase(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Coding problems</h1>
          <p className="text-muted-foreground">Author problems, their limits, and their test cases.</p>
        </div>
        <Button onClick={openCreate}>
          <PlusIcon />
          Add problem
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All problems</CardTitle>
          <CardDescription>Search by title or slug, or filter by difficulty and publish state.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search problems…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={difficulty} onValueChange={(value) => setDifficulty(value ?? ALL_DIFFICULTIES)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All difficulties" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_DIFFICULTIES}>All difficulties</SelectItem>
                {DIFFICULTIES.map((d) => (
                  <SelectItem key={d} value={d}>
                    {d.charAt(0) + d.slice(1).toLowerCase()}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={publishedFilter} onValueChange={(value) => setPublishedFilter(value ?? ALL_PUBLISHED)}>
              <SelectTrigger className="w-40">
                <SelectValue placeholder="All states" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_PUBLISHED}>All states</SelectItem>
                <SelectItem value="true">Published</SelectItem>
                <SelectItem value="false">Unpublished</SelectItem>
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
                <TableHead>Slug</TableHead>
                <TableHead>Difficulty</TableHead>
                <TableHead>Test cases</TableHead>
                <TableHead>Published</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    No problems found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((problem) => (
                  <TableRow key={problem.id}>
                    <TableCell className="font-medium">{problem.title}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{problem.slug}</TableCell>
                    <TableCell>
                      <DifficultyBadge difficulty={problem.difficulty} />
                    </TableCell>
                    <TableCell>
                      <Button variant="link" size="sm" className="h-auto p-0" onClick={() => openTestCases(problem)}>
                        {problem.sampleTestCaseCount + problem.hiddenTestCaseCount} test cases
                      </Button>
                    </TableCell>
                    <TableCell>
                      <Badge variant={problem.published ? "default" : "outline"}>
                        {problem.published ? "Published" : "Draft"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(problem)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(problem);
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

      {/* --- create / edit problem dialog --- */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit problem" : "Add problem"}</DialogTitle>
              <DialogDescription>
                {editing ? `Update ${editing.title}.` : "Create a new coding problem."}
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
                  <Label htmlFor="problem-slug">Slug</Label>
                  <Input
                    id="problem-slug"
                    value={form.slug}
                    onChange={(event) => setForm((f) => ({ ...f, slug: event.target.value }))}
                    aria-invalid={!!fieldErrors.slug}
                    placeholder="two-sum"
                  />
                  {fieldErrors.slug && <p className="text-xs text-destructive">{fieldErrors.slug}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="problem-title">Title</Label>
                  <Input
                    id="problem-title"
                    value={form.title}
                    maxLength={200}
                    onChange={(event) => setForm((f) => ({ ...f, title: event.target.value }))}
                    aria-invalid={!!fieldErrors.title}
                    placeholder="Two Sum"
                  />
                  {fieldErrors.title && <p className="text-xs text-destructive">{fieldErrors.title}</p>}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="problem-description">Description</Label>
                <textarea
                  id="problem-description"
                  className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.description}
                  onChange={(event) => setForm((f) => ({ ...f, description: event.target.value }))}
                  aria-invalid={!!fieldErrors.description}
                />
                {fieldErrors.description && (
                  <p className="text-xs text-destructive">{fieldErrors.description}</p>
                )}
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="problem-input-format">Input format</Label>
                  <textarea
                    id="problem-input-format"
                    className="min-h-16 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={form.inputFormat}
                    onChange={(event) => setForm((f) => ({ ...f, inputFormat: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="problem-output-format">Output format</Label>
                  <textarea
                    id="problem-output-format"
                    className="min-h-16 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={form.outputFormat}
                    onChange={(event) => setForm((f) => ({ ...f, outputFormat: event.target.value }))}
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="problem-constraints">Constraints</Label>
                <textarea
                  id="problem-constraints"
                  className="min-h-16 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.constraintsText}
                  onChange={(event) => setForm((f) => ({ ...f, constraintsText: event.target.value }))}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="problem-sample-input">Sample input</Label>
                  <textarea
                    id="problem-sample-input"
                    className="min-h-16 w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={form.sampleInput}
                    onChange={(event) => setForm((f) => ({ ...f, sampleInput: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="problem-sample-output">Sample output</Label>
                  <textarea
                    id="problem-sample-output"
                    className="min-h-16 w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={form.sampleOutput}
                    onChange={(event) => setForm((f) => ({ ...f, sampleOutput: event.target.value }))}
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label htmlFor="problem-difficulty">Difficulty</Label>
                  <Select
                    value={form.difficulty}
                    onValueChange={(value) => value && setForm((f) => ({ ...f, difficulty: value as ProblemDifficulty }))}
                  >
                    <SelectTrigger id="problem-difficulty" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {DIFFICULTIES.map((d) => (
                        <SelectItem key={d} value={d}>
                          {d.charAt(0) + d.slice(1).toLowerCase()}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="problem-time-limit">Time limit (ms)</Label>
                  <Input
                    id="problem-time-limit"
                    type="number"
                    min={100}
                    max={15000}
                    value={form.timeLimitMs}
                    onChange={(event) => setForm((f) => ({ ...f, timeLimitMs: event.target.value }))}
                    aria-invalid={!!fieldErrors.timeLimitMs}
                  />
                  {fieldErrors.timeLimitMs && (
                    <p className="text-xs text-destructive">{fieldErrors.timeLimitMs}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="problem-memory-limit">Memory limit (KB)</Label>
                  <Input
                    id="problem-memory-limit"
                    type="number"
                    min={16384}
                    max={512000}
                    value={form.memoryLimitKb}
                    onChange={(event) => setForm((f) => ({ ...f, memoryLimitKb: event.target.value }))}
                    aria-invalid={!!fieldErrors.memoryLimitKb}
                  />
                  {fieldErrors.memoryLimitKb && (
                    <p className="text-xs text-destructive">{fieldErrors.memoryLimitKb}</p>
                  )}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="problem-tags">Tags</Label>
                <Input
                  id="problem-tags"
                  value={form.tags}
                  onChange={(event) => setForm((f) => ({ ...f, tags: event.target.value }))}
                  placeholder="arrays, hash-map"
                />
                <p className="text-xs text-muted-foreground">Comma-separated.</p>
              </div>

              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="size-4 rounded border-border"
                  checked={form.published}
                  onChange={(event) => setForm((f) => ({ ...f, published: event.target.checked }))}
                />
                Published (visible to students)
              </label>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create problem"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete problem?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes <strong>{deleteTarget?.title}</strong> and its test cases. Problems
                with existing submissions or contest usage cannot be deleted.
              </>
            )}
          </>
        }
        confirmLabel="Delete"
        destructive
        isConfirming={isDeleting}
        onConfirm={handleDelete}
      />

      {/* --- test case management dialog --- */}
      <Dialog open={testCaseProblem !== null} onOpenChange={(open) => !open && setTestCaseProblem(null)}>
        <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Test cases — {testCaseProblem?.title}</DialogTitle>
            <DialogDescription>
              Sample cases are shown to students; hidden cases drive the verdict but are never revealed.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            {testCasesError && (
              <Alert variant="destructive">
                <AlertDescription>{testCasesError}</AlertDescription>
              </Alert>
            )}

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">#</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Weight</TableHead>
                  <TableHead>Input</TableHead>
                  <TableHead>Expected output</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoadingTestCases && (
                  <TableRow>
                    <TableCell colSpan={6} className="py-6 text-center text-muted-foreground">
                      Loading…
                    </TableCell>
                  </TableRow>
                )}
                {!isLoadingTestCases && testCases.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="py-6 text-center text-muted-foreground">
                      No test cases yet — this problem cannot be judged until at least one exists.
                    </TableCell>
                  </TableRow>
                )}
                {!isLoadingTestCases &&
                  testCases.map((tc) => (
                    <TableRow key={tc.id}>
                      <TableCell>{tc.ordinal}</TableCell>
                      <TableCell>
                        <Badge variant={tc.isSample ? "secondary" : "outline"}>
                          {tc.isSample ? "Sample" : "Hidden"}
                        </Badge>
                      </TableCell>
                      <TableCell>{tc.weight}</TableCell>
                      <TableCell className="max-w-40 truncate font-mono text-xs">{tc.input || "(empty)"}</TableCell>
                      <TableCell className="max-w-40 truncate font-mono text-xs">
                        {tc.expectedOutput || "(empty)"}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" onClick={() => startEditTestCase(tc)}>
                            <PencilIcon />
                            <span className="sr-only">Edit</span>
                          </Button>
                          <Button variant="ghost" size="icon" onClick={() => setDeleteTestCaseTarget(tc)}>
                            <Trash2Icon />
                            <span className="sr-only">Delete</span>
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>

            <form onSubmit={handleSaveTestCase} className="space-y-3 rounded-lg border border-border p-3">
              <p className="text-sm font-medium">{testCaseEditing ? `Edit test case #${testCaseEditing.ordinal}` : "Add test case"}</p>
              {testCaseFormError && (
                <Alert variant="destructive">
                  <AlertDescription>{testCaseFormError}</AlertDescription>
                </Alert>
              )}
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label htmlFor="tc-ordinal">Ordinal</Label>
                  <Input
                    id="tc-ordinal"
                    type="number"
                    min={1}
                    value={testCaseForm.ordinal}
                    onChange={(event) => setTestCaseForm((f) => ({ ...f, ordinal: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="tc-weight">Weight</Label>
                  <Input
                    id="tc-weight"
                    type="number"
                    min={1}
                    value={testCaseForm.weight}
                    onChange={(event) => setTestCaseForm((f) => ({ ...f, weight: event.target.value }))}
                  />
                </div>
                <label className="flex items-center gap-2 self-end pb-2 text-sm">
                  <input
                    type="checkbox"
                    className="size-4 rounded border-border"
                    checked={testCaseForm.isSample}
                    onChange={(event) => setTestCaseForm((f) => ({ ...f, isSample: event.target.checked }))}
                  />
                  Sample (shown to students)
                </label>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="tc-input">Input</Label>
                  <textarea
                    id="tc-input"
                    className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={testCaseForm.input}
                    onChange={(event) => setTestCaseForm((f) => ({ ...f, input: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="tc-expected">Expected output</Label>
                  <textarea
                    id="tc-expected"
                    className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={testCaseForm.expectedOutput}
                    onChange={(event) => setTestCaseForm((f) => ({ ...f, expectedOutput: event.target.value }))}
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2">
                {testCaseEditing && (
                  <Button type="button" variant="outline" onClick={resetTestCaseForm}>
                    Cancel edit
                  </Button>
                )}
                <Button type="submit" disabled={isSavingTestCase}>
                  {isSavingTestCase ? "Saving…" : testCaseEditing ? "Save changes" : "Add test case"}
                </Button>
              </div>
            </form>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setTestCaseProblem(null)}>
              Close
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTestCaseTarget !== null}
        onOpenChange={(open) => !open && setDeleteTestCaseTarget(null)}
        title="Delete test case?"
        description={`This permanently deletes test case #${deleteTestCaseTarget?.ordinal ?? ""}.`}
        confirmLabel="Delete"
        destructive
        isConfirming={isDeletingTestCase}
        onConfirm={handleDeleteTestCase}
      />
    </div>
  );
}
