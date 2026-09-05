import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";

import { ClassScopePicker } from "@/components/academics/ClassScopePicker";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as examService from "@/services/examService";
import type { ExamListParams } from "@/services/examService";
import type {
  ExamRequest,
  ExamResponse,
  ExamStatus,
  ExamType,
  ExamUpdateRequest,
  TeachingClassResponse,
} from "@/types/academicOps";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const EXAM_TYPES: ExamType[] = ["INTERNAL_1", "INTERNAL_2", "INTERNAL_3", "ASSIGNMENT", "QUIZ", "MODEL", "SEMESTER"];
const EXAM_STATUSES: ExamStatus[] = ["SCHEDULED", "COMPLETED", "CANCELLED"];

const EXAM_TYPE_LABELS: Record<ExamType, string> = {
  INTERNAL_1: "Internal 1",
  INTERNAL_2: "Internal 2",
  INTERNAL_3: "Internal 3",
  ASSIGNMENT: "Assignment",
  QUIZ: "Quiz",
  MODEL: "Model",
  SEMESTER: "Semester",
};

const STATUS_BADGE_VARIANT: Record<ExamStatus, "default" | "secondary" | "destructive"> = {
  SCHEDULED: "default",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

const FORM_FIELDS = ["title", "examType", "examDate", "maximumMarks", "status"] as const;

interface FormState {
  title: string;
  examType: ExamType;
  examDate: string;
  maximumMarks: string;
  status: ExamStatus;
}

function emptyForm(): FormState {
  return { title: "", examType: "INTERNAL_1", examDate: "", maximumMarks: "100", status: "SCHEDULED" };
}

function validateMaximumMarks(raw: string): string | null {
  const value = Number(raw);
  if (!raw || Number.isNaN(value)) return "Maximum marks is required.";
  if (value < 0.01 || value > 1000) return "Maximum marks must be between 0.01 and 1000.";
  if (!/^\d{1,4}(\.\d{1,2})?$/.test(raw.trim())) return "At most 4 integer digits and 2 decimal places.";
  return null;
}

export default function FacultyExamsPage() {
  const [selectedClass, setSelectedClass] = useState<TeachingClassResponse | null>(null);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Exams</h1>
        <p className="text-muted-foreground">Schedule and manage exams for your classes.</p>
      </div>

      <Card>
        <CardContent className="pt-6">
          <ClassScopePicker value={selectedClass} onChange={setSelectedClass} />
        </CardContent>
      </Card>

      {selectedClass && <ExamsPanel key={selectedClass.assignmentId} scope={selectedClass} />}
    </div>
  );
}

interface ExamsPanelProps {
  scope: TeachingClassResponse;
}

function ExamsPanel({ scope }: ExamsPanelProps) {
  const filters = useMemo<Pick<ExamListParams, "subjectId" | "academicYear" | "semester" | "section">>(
    () => ({
      subjectId: scope.subjectId,
      academicYear: scope.academicYear,
      semester: scope.semester,
      section: scope.section,
    }),
    [scope.subjectId, scope.academicYear, scope.semester, scope.section],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    examService.listExams,
    filters,
    { sort: "examDate,asc" },
  );

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ExamResponse | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<ExamResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm());
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(exam: ExamResponse) {
    setEditing(exam);
    setForm({
      title: exam.title,
      examType: exam.examType,
      examDate: exam.examDate,
      maximumMarks: String(exam.maximumMarks),
      status: exam.status,
    });
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.title.trim()) errors.title = "Title is required.";
    if (!form.examDate) errors.examDate = "Exam date is required.";
    const marksError = validateMaximumMarks(form.maximumMarks);
    if (marksError) errors.maximumMarks = marksError;

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    setIsSaving(true);
    try {
      if (editing) {
        const payload: ExamUpdateRequest = {
          title: form.title.trim(),
          examType: form.examType,
          examDate: form.examDate,
          maximumMarks: Number(form.maximumMarks),
          status: form.status,
        };
        await examService.updateExam(editing.id, payload);
      } else {
        const payload: ExamRequest = {
          subjectId: scope.subjectId,
          title: form.title.trim(),
          examType: form.examType,
          academicYear: scope.academicYear,
          semester: scope.semester,
          section: scope.section,
          examDate: form.examDate,
          maximumMarks: Number(form.maximumMarks),
        };
        await examService.createExam(payload);
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
        setFormError(extractErrorMessage(err, "Failed to save the exam."));
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
      await examService.deleteExam(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(
        extractErrorMessage(err, "Failed to delete this exam. Marks may already be entered for it."),
      );
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle>
                {scope.subjectCode} — {scope.subjectName}
              </CardTitle>
              <CardDescription>
                {scope.academicYear} · Sem {scope.semester} · Sec {scope.section}
              </CardDescription>
            </div>
            <Button onClick={openCreate}>
              <PlusIcon />
              Add exam
            </Button>
          </div>
          <Input
            placeholder="Search exam titles…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="mt-2 max-w-sm"
          />
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
                <TableHead>Type</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Max marks</TableHead>
                <TableHead>Status</TableHead>
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
                    No exams scheduled for this class yet.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((exam) => (
                  <TableRow key={exam.id}>
                    <TableCell className="font-medium">{exam.title}</TableCell>
                    <TableCell>{EXAM_TYPE_LABELS[exam.examType]}</TableCell>
                    <TableCell>{exam.examDate}</TableCell>
                    <TableCell>{exam.maximumMarks}</TableCell>
                    <TableCell>
                      <Badge variant={STATUS_BADGE_VARIANT[exam.status]}>{exam.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(exam)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(exam);
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
        <DialogContent>
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit exam" : "Add exam"}</DialogTitle>
              <DialogDescription>
                {editing
                  ? `Update ${editing.title}. The class scope cannot be changed after creation.`
                  : `Schedule a new exam for ${scope.subjectCode} · ${scope.academicYear} Sem ${scope.semester} Sec ${scope.section}.`}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="exam-title">Title</Label>
                <Input
                  id="exam-title"
                  value={form.title}
                  maxLength={150}
                  onChange={(event) => setForm((f) => ({ ...f, title: event.target.value }))}
                  aria-invalid={!!fieldErrors.title}
                  placeholder="Mid-semester internal"
                />
                {fieldErrors.title && <p className="text-xs text-destructive">{fieldErrors.title}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="exam-type">Exam type</Label>
                <Select
                  value={form.examType}
                  onValueChange={(value) => value && setForm((f) => ({ ...f, examType: value as ExamType }))}
                >
                  <SelectTrigger id="exam-type" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {EXAM_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>
                        {EXAM_TYPE_LABELS[type]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="exam-date">Exam date</Label>
                  <Input
                    id="exam-date"
                    type="date"
                    value={form.examDate}
                    onChange={(event) => setForm((f) => ({ ...f, examDate: event.target.value }))}
                    aria-invalid={!!fieldErrors.examDate}
                  />
                  {fieldErrors.examDate && <p className="text-xs text-destructive">{fieldErrors.examDate}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="exam-max-marks">Maximum marks</Label>
                  <Input
                    id="exam-max-marks"
                    type="number"
                    min={0.01}
                    max={1000}
                    step={0.01}
                    value={form.maximumMarks}
                    onChange={(event) => setForm((f) => ({ ...f, maximumMarks: event.target.value }))}
                    aria-invalid={!!fieldErrors.maximumMarks}
                  />
                  {fieldErrors.maximumMarks && (
                    <p className="text-xs text-destructive">{fieldErrors.maximumMarks}</p>
                  )}
                </div>
              </div>

              {editing && (
                <div className="space-y-1.5">
                  <Label htmlFor="exam-status">Status</Label>
                  <Select
                    value={form.status}
                    onValueChange={(value) => value && setForm((f) => ({ ...f, status: value as ExamStatus }))}
                  >
                    <SelectTrigger id="exam-status" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {EXAM_STATUSES.map((status) => (
                        <SelectItem key={status} value={status}>
                          {status}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}

              <div className="rounded-lg border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                Class scope: {scope.subjectCode} · {scope.academicYear} · Sem {scope.semester} · Sec{" "}
                {scope.section} — fixed for this exam and cannot be changed after creation.
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create exam"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete exam?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes <strong>{deleteTarget?.title}</strong>. Exams with marks already
                entered cannot be deleted.
              </>
            )}
          </>
        }
        confirmLabel="Delete"
        destructive
        isConfirming={isDeleting}
        onConfirm={handleDelete}
      />
    </>
  );
}
