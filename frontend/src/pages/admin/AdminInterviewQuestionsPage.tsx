import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { CATEGORY_LABELS, DIFFICULTY_BADGE_VARIANT, DIFFICULTY_LABELS } from "@/components/interview/interviewLabels";
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
import * as interviewQuestionService from "@/services/interviewQuestionService";
import type {
  InterviewDifficulty,
  InterviewQuestionCategory,
  InterviewQuestionCreateRequest,
  InterviewQuestionListParams,
  InterviewQuestionResponse,
} from "@/types/interview";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

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

const FORM_FIELDS = ["category", "difficulty", "question", "answer", "explanation", "companyName", "tags"] as const;

interface FormState {
  category: InterviewQuestionCategory | "";
  difficulty: InterviewDifficulty | "";
  question: string;
  answer: string;
  explanation: string;
  companyName: string;
  tags: string;
}

function emptyForm(): FormState {
  return { category: "", difficulty: "", question: "", answer: "", explanation: "", companyName: "", tags: "" };
}

function fromQuestion(q: InterviewQuestionResponse): FormState {
  return {
    category: q.category,
    difficulty: q.difficulty,
    question: q.question,
    answer: q.answer ?? "",
    explanation: q.explanation ?? "",
    companyName: q.companyName ?? "",
    tags: q.tags ?? "",
  };
}

/**
 * `/admin/interview-questions` — paged global-bank table (source=CURATED,
 * ownerStudent=null on every row this page creates or edits). Every response DTO field
 * this page writes exists on `InterviewQuestionCreateRequest`/`UpdateRequest`; nothing
 * extra is sent.
 */
export default function AdminInterviewQuestionsPage() {
  const [category, setCategory] = useState(ALL);
  const [difficulty, setDifficulty] = useState(ALL);

  const filters = useMemo(
    () => ({
      category: category === ALL ? undefined : (category as InterviewQuestionCategory),
      difficulty: difficulty === ALL ? undefined : (difficulty as InterviewDifficulty),
    }),
    [category, difficulty],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable<
    InterviewQuestionResponse,
    Omit<InterviewQuestionListParams, "search" | "page" | "size" | "sort">
  >(interviewQuestionService.listInterviewQuestions, filters, { pageSize: 20, sort: "createdAt,desc" });

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<InterviewQuestionResponse | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<InterviewQuestionResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm());
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(question: InterviewQuestionResponse) {
    setEditing(question);
    setForm(fromQuestion(question));
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.category) errors.category = "Category is required.";
    if (!form.question.trim()) errors.question = "Question text is required.";
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    const payload: InterviewQuestionCreateRequest = {
      category: form.category as InterviewQuestionCategory,
      difficulty: form.difficulty || null,
      question: form.question.trim(),
      answer: form.answer.trim() || null,
      explanation: form.explanation.trim() || null,
      companyName: form.companyName.trim() || null,
      tags: form.tags.trim() || null,
    };

    setIsSaving(true);
    try {
      if (editing) {
        await interviewQuestionService.updateInterviewQuestion(editing.id, payload);
      } else {
        await interviewQuestionService.createInterviewQuestion(payload);
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
        setFormError(extractErrorMessage(err, "Failed to save this question."));
      }
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await interviewQuestionService.deleteInterviewQuestion(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this question."));
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Interview questions</h1>
          <p className="text-muted-foreground">The global question bank every student can practice from.</p>
        </div>
        <Button onClick={openCreate}>
          <PlusIcon />
          Add question
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Question bank</CardTitle>
          <CardDescription>Search by question text, or filter by category and difficulty.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search question text…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-56"
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
                <TableHead>Category</TableHead>
                <TableHead>Difficulty</TableHead>
                <TableHead>Question</TableHead>
                <TableHead>Company</TableHead>
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
                    No questions match these filters.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((q) => (
                  <TableRow key={q.id}>
                    <TableCell>
                      <Badge variant="outline">{CATEGORY_LABELS[q.category]}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={DIFFICULTY_BADGE_VARIANT[q.difficulty]}>{DIFFICULTY_LABELS[q.difficulty]}</Badge>
                    </TableCell>
                    <TableCell className="max-w-md truncate">{q.question}</TableCell>
                    <TableCell className="text-muted-foreground">{q.companyName || "—"}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(q)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(q);
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

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit question" : "Add question"}</DialogTitle>
              <DialogDescription>
                {editing ? "Update this global-bank question." : "Adds a new question to the global bank."}
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
                  <Label htmlFor="q-category">Category</Label>
                  <Select
                    value={form.category}
                    onValueChange={(value) =>
                      value && setForm((f) => ({ ...f, category: value as InterviewQuestionCategory }))
                    }
                  >
                    <SelectTrigger id="q-category" className="w-full" aria-invalid={!!fieldErrors.category}>
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
                  {fieldErrors.category && <p className="text-xs text-destructive">{fieldErrors.category}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="q-difficulty">Difficulty</Label>
                  <Select
                    value={form.difficulty || ALL}
                    onValueChange={(value) =>
                      setForm((f) => ({ ...f, difficulty: value === ALL ? "" : (value as InterviewDifficulty) }))
                    }
                  >
                    <SelectTrigger id="q-difficulty" className="w-full">
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
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="q-question">Question</Label>
                <textarea
                  id="q-question"
                  className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.question}
                  onChange={(e) => setForm((f) => ({ ...f, question: e.target.value }))}
                  aria-invalid={!!fieldErrors.question}
                />
                {fieldErrors.question && <p className="text-xs text-destructive">{fieldErrors.question}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="q-answer">Answer (optional)</Label>
                <textarea
                  id="q-answer"
                  className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.answer}
                  onChange={(e) => setForm((f) => ({ ...f, answer: e.target.value }))}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="q-explanation">Explanation (optional)</Label>
                <textarea
                  id="q-explanation"
                  className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.explanation}
                  onChange={(e) => setForm((f) => ({ ...f, explanation: e.target.value }))}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="q-company">Company (optional)</Label>
                  <Input
                    id="q-company"
                    value={form.companyName}
                    maxLength={150}
                    onChange={(e) => setForm((f) => ({ ...f, companyName: e.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="q-tags">Tags (optional)</Label>
                  <Input
                    id="q-tags"
                    value={form.tags}
                    maxLength={255}
                    onChange={(e) => setForm((f) => ({ ...f, tags: e.target.value }))}
                    placeholder="comma, separated, tags"
                  />
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Add question"}
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
            <>This permanently removes this question from the global bank for every student.</>
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
