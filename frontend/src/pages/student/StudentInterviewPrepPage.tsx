import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { AxiosError } from "axios";
import { SparklesIcon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { QuestionBankItem } from "@/components/interview/QuestionBankItem";
import { CATEGORY_LABELS, DIFFICULTY_LABELS } from "@/components/interview/interviewLabels";
import { Alert, AlertDescription } from "@/components/ui/alert";
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
import { useServerTable } from "@/hooks/useServerTable";
import * as interviewQuestionService from "@/services/interviewQuestionService";
import type {
  InterviewDifficulty,
  InterviewProgressSummaryResponse,
  InterviewQuestionCategory,
  InterviewQuestionListParams,
  InterviewQuestionResponse,
} from "@/types/interview";
import { extractErrorMessage } from "@/utils/apiError";

const ALL = "__ALL__";
const CATEGORIES: InterviewQuestionCategory[] = [
  "TECHNICAL",
  "HR",
  "BEHAVIOURAL",
  "CODING",
  "APTITUDE",
  "COMPANY_SPECIFIC",
];
const DIFFICULTIES: InterviewDifficulty[] = ["EASY", "MEDIUM", "HARD"];

function isAxiosStatus(err: unknown, status: number): boolean {
  return err instanceof AxiosError && err.response?.status === status;
}

interface GenerateForm {
  category: InterviewQuestionCategory | "";
  difficulty: InterviewDifficulty | "";
  topic: string;
  companyName: string;
  count: string;
}

function emptyGenerateForm(): GenerateForm {
  return { category: "", difficulty: "", topic: "", companyName: "", count: "" };
}

/**
 * `/student/interview-prep` — the question bank. Server-side filtering only, driven by
 * `useServerTable`: category, difficulty, a company filter (separately debounced from
 * the shared search box), and STUDENT-only toggles for bookmarked/completed/mine.
 */
export default function StudentInterviewPrepPage() {
  const [category, setCategory] = useState(ALL);
  const [difficulty, setDifficulty] = useState(ALL);
  const [companyFilter, setCompanyFilter] = useState("");
  const [debouncedCompany, setDebouncedCompany] = useState("");
  const [bookmarkedOnly, setBookmarkedOnly] = useState(false);
  const [completedOnly, setCompletedOnly] = useState(false);
  const [mineOnly, setMineOnly] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedCompany(companyFilter), 350);
    return () => clearTimeout(timer);
  }, [companyFilter]);

  const filters = useMemo(
    () => ({
      category: category === ALL ? undefined : (category as InterviewQuestionCategory),
      difficulty: difficulty === ALL ? undefined : (difficulty as InterviewDifficulty),
      companyName: debouncedCompany || undefined,
      bookmarked: bookmarkedOnly || undefined,
      completed: completedOnly || undefined,
      mine: mineOnly || undefined,
    }),
    [category, difficulty, debouncedCompany, bookmarkedOnly, completedOnly, mineOnly],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable<
    InterviewQuestionResponse,
    Omit<InterviewQuestionListParams, "search" | "page" | "size" | "sort">
  >(interviewQuestionService.listInterviewQuestions, filters, { pageSize: 20, sort: "createdAt,desc" });

  // Local mirror of the current page's rows so a progress-toggle response updates
  // exactly the row that changed, using the real PUT response — never an optimistic
  // local flip.
  const [rows, setRows] = useState<InterviewQuestionResponse[]>([]);
  // Re-sync the mirror during render whenever a new page of `data` arrives, rather
  // than via an effect — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes.
  const [rowsForData, setRowsForData] = useState(data);
  if (data !== rowsForData) {
    setRowsForData(data);
    setRows(data?.content ?? []);
  }

  function handleQuestionChanged(updated: InterviewQuestionResponse) {
    setRows((prev) => prev.map((q) => (q.id === updated.id ? updated : q)));
    loadSummary();
  }

  // --- Progress summary -----------------------------------------------------------
  const [summary, setSummary] = useState<InterviewProgressSummaryResponse | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);

  function loadSummary() {
    interviewQuestionService
      .getProgressSummary()
      .then(setSummary)
      .catch((err) => setSummaryError(extractErrorMessage(err, "Failed to load your progress.")))
      .finally(() => setSummaryLoading(false));
  }

  useEffect(() => {
    loadSummary();
  }, []);

  // --- Delete (own AI-generated questions only) -----------------------------------
  const [deleteTarget, setDeleteTarget] = useState<InterviewQuestionResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await interviewQuestionService.deleteInterviewQuestion(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
      loadSummary();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this question."));
    } finally {
      setDeleting(false);
    }
  }

  // --- Generate practice questions -------------------------------------------------
  const [generateOpen, setGenerateOpen] = useState(false);
  const [generateForm, setGenerateForm] = useState<GenerateForm>(emptyGenerateForm());
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [generateSuccess, setGenerateSuccess] = useState<string | null>(null);
  const [generating, setGenerating] = useState(false);

  function openGenerate() {
    setGenerateForm(emptyGenerateForm());
    setGenerateError(null);
    setGenerateSuccess(null);
    setGenerateOpen(true);
  }

  async function handleGenerateSubmit(event: FormEvent) {
    event.preventDefault();
    setGenerateError(null);
    setGenerateSuccess(null);
    if (!generateForm.category) {
      setGenerateError("Choose a category.");
      return;
    }
    setGenerating(true);
    try {
      const result = await interviewQuestionService.generateQuestions({
        category: generateForm.category,
        difficulty: generateForm.difficulty || null,
        topic: generateForm.topic.trim() || null,
        companyName: generateForm.companyName.trim() || null,
        count: generateForm.count.trim() ? Number(generateForm.count.trim()) : null,
      });
      setGenerateSuccess(`Generated ${result.count} question${result.count === 1 ? "" : "s"} using ${result.model}.`);
      setMineOnly(true);
      refresh();
      loadSummary();
    } catch (err) {
      if (isAxiosStatus(err, 429)) {
        setGenerateError(extractErrorMessage(err, "Rate limit exceeded. Try again in a moment."));
      } else if (isAxiosStatus(err, 503)) {
        setGenerateError(
          extractErrorMessage(err, "AI is not configured on this server, so practice questions cannot be generated."),
        );
      } else {
        setGenerateError(extractErrorMessage(err, "Failed to generate practice questions."));
      }
    } finally {
      setGenerating(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Interview prep</h1>
          <p className="text-muted-foreground">Practice from the question bank and track your progress.</p>
        </div>
        <Button onClick={openGenerate}>
          <SparklesIcon />
          Generate practice questions
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Your progress</CardTitle>
          <CardDescription>Across every question visible to you.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {summaryError && (
            <Alert variant="destructive">
              <AlertDescription>{summaryError}</AlertDescription>
            </Alert>
          )}
          {summaryLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {!summaryLoading && summary && (
            <>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Total</p>
                  <p className="text-xl font-semibold">{summary.totalQuestions}</p>
                </div>
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Completed</p>
                  <p className="text-xl font-semibold">{summary.completed}</p>
                </div>
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Not started</p>
                  <p className="text-xl font-semibold">{summary.notStarted}</p>
                </div>
                <div className="rounded-lg border border-border p-3">
                  <p className="text-xs text-muted-foreground">Bookmarked</p>
                  <p className="text-xl font-semibold">{summary.bookmarked}</p>
                </div>
              </div>
              <div className="space-y-1.5">
                {summary.byCategory.map((row) => {
                  const pct = row.total === 0 ? 0 : Math.round((row.completed / row.total) * 100);
                  return (
                    <div key={row.category} className="flex items-center gap-2 text-xs">
                      <span className="w-32 shrink-0 text-muted-foreground">{CATEGORY_LABELS[row.category]}</span>
                      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                        <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
                      </div>
                      <span className="w-16 shrink-0 text-right text-muted-foreground">
                        {row.completed}/{row.total}
                      </span>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Question bank</CardTitle>
          <CardDescription>Search and filter the bank, expand a question to see its answer.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-2">
            <Input
              placeholder="Search question text…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-56"
            />
            <Input
              placeholder="Company…"
              value={companyFilter}
              onChange={(e) => setCompanyFilter(e.target.value)}
              className="w-40"
            />
            <Select value={category} onValueChange={(value) => value && setCategory(value)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All categories" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All categories</SelectItem>
                {CATEGORIES.map((c) => (
                  <SelectItem key={c} value={c}>
                    {CATEGORY_LABELS[c]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={difficulty} onValueChange={(value) => value && setDifficulty(value)}>
              <SelectTrigger className="w-36">
                <SelectValue placeholder="Any difficulty" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Any difficulty</SelectItem>
                {DIFFICULTIES.map((d) => (
                  <SelectItem key={d} value={d}>
                    {DIFFICULTY_LABELS[d]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              type="button"
              variant={bookmarkedOnly ? "secondary" : "outline"}
              size="sm"
              onClick={() => setBookmarkedOnly((v) => !v)}
            >
              Bookmarked only
            </Button>
            <Button
              type="button"
              variant={completedOnly ? "secondary" : "outline"}
              size="sm"
              onClick={() => setCompletedOnly((v) => !v)}
            >
              Completed only
            </Button>
            <Button
              type="button"
              variant={mineOnly ? "secondary" : "outline"}
              size="sm"
              onClick={() => setMineOnly((v) => !v)}
            >
              My AI questions
            </Button>
          </div>

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="space-y-2">
            {isLoading && <p className="py-8 text-center text-sm text-muted-foreground">Loading…</p>}
            {!isLoading && rows.length === 0 && (
              <p className="py-8 text-center text-sm text-muted-foreground">
                No questions match these filters.
              </p>
            )}
            {!isLoading &&
              rows.map((question) => (
                <QuestionBankItem
                  key={question.id}
                  question={question}
                  onChanged={handleQuestionChanged}
                  onDelete={(q) => {
                    setDeleteError(null);
                    setDeleteTarget(q);
                  }}
                />
              ))}
          </div>

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

      <Dialog open={generateOpen} onOpenChange={setGenerateOpen}>
        <DialogContent>
          <form onSubmit={handleGenerateSubmit}>
            <DialogHeader>
              <DialogTitle>Generate practice questions</DialogTitle>
              <DialogDescription>
                Creates new questions in your own private bank (visible only to you) using AI.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              {generateError && (
                <Alert variant="destructive">
                  <AlertDescription>{generateError}</AlertDescription>
                </Alert>
              )}
              {generateSuccess && (
                <Alert>
                  <AlertDescription>{generateSuccess}</AlertDescription>
                </Alert>
              )}
              <div className="space-y-1.5">
                <Label htmlFor="gen-category">Category</Label>
                <Select
                  value={generateForm.category}
                  onValueChange={(value) =>
                    value && setGenerateForm((f) => ({ ...f, category: value as InterviewQuestionCategory }))
                  }
                >
                  <SelectTrigger id="gen-category" className="w-full">
                    <SelectValue placeholder="Select a category" />
                  </SelectTrigger>
                  <SelectContent>
                    {CATEGORIES.map((c) => (
                      <SelectItem key={c} value={c}>
                        {CATEGORY_LABELS[c]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="gen-difficulty">Difficulty</Label>
                  <Select
                    value={generateForm.difficulty || ALL}
                    onValueChange={(value) =>
                      setGenerateForm((f) => ({
                        ...f,
                        difficulty: value === ALL ? "" : (value as InterviewDifficulty),
                      }))
                    }
                  >
                    <SelectTrigger id="gen-difficulty" className="w-full">
                      <SelectValue placeholder="Medium" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={ALL}>Medium (default)</SelectItem>
                      {DIFFICULTIES.map((d) => (
                        <SelectItem key={d} value={d}>
                          {DIFFICULTY_LABELS[d]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="gen-count">Count</Label>
                  <Input
                    id="gen-count"
                    type="number"
                    min={1}
                    max={10}
                    value={generateForm.count}
                    onChange={(e) => setGenerateForm((f) => ({ ...f, count: e.target.value }))}
                    placeholder="5"
                  />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="gen-topic">Topic (optional)</Label>
                <Input
                  id="gen-topic"
                  value={generateForm.topic}
                  maxLength={200}
                  onChange={(e) => setGenerateForm((f) => ({ ...f, topic: e.target.value }))}
                  placeholder="e.g. System design basics"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="gen-company">Company (optional)</Label>
                <Input
                  id="gen-company"
                  value={generateForm.companyName}
                  maxLength={150}
                  onChange={(e) => setGenerateForm((f) => ({ ...f, companyName: e.target.value }))}
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setGenerateOpen(false)}>
                Close
              </Button>
              <Button type="submit" disabled={generating}>
                {generating ? "Generating…" : "Generate"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete this question?"
        description={
          deleteError ? (
            <span className="text-destructive">{deleteError}</span>
          ) : (
            <>This permanently deletes this question from your private bank.</>
          )
        }
        confirmLabel="Delete"
        destructive
        isConfirming={deleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
