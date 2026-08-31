import { useEffect, useState } from "react";
import { RefreshCwIcon } from "lucide-react";

import { ClassScopePicker } from "@/components/academics/ClassScopePicker";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
import * as examService from "@/services/examService";
import * as marksService from "@/services/marksService";
import type { ExamResponse, ExamType, MarksEntry, MarksEntrySheetResponse, TeachingClassResponse } from "@/types/academicOps";
import { extractErrorMessage } from "@/utils/apiError";

const EXAM_TYPE_LABELS: Record<ExamType, string> = {
  INTERNAL_1: "Internal 1",
  INTERNAL_2: "Internal 2",
  INTERNAL_3: "Internal 3",
  ASSIGNMENT: "Assignment",
  QUIZ: "Quiz",
  MODEL: "Model",
  SEMESTER: "Semester",
};

interface RowState {
  marksObtained: string;
  remarks: string;
}

/** Digits(integer = 4, fraction = 2) — matches the backend `@Digits` constraint. */
function digitsOk(raw: string): boolean {
  return /^\d{1,4}(\.\d{1,2})?$/.test(raw.trim());
}

function validateMark(raw: string, maximumMarks: number): string | null {
  const trimmed = raw.trim();
  if (trimmed === "") return null; // not being submitted for this student
  const value = Number(trimmed);
  if (Number.isNaN(value)) return "Not a number.";
  if (!digitsOk(trimmed)) return "At most 4 integer digits and 2 decimal places.";
  if (value < 0) return "Cannot be negative.";
  if (value > maximumMarks) return `Cannot exceed ${maximumMarks}.`;
  return null;
}

export default function FacultyMarksPage() {
  const [selectedClass, setSelectedClass] = useState<TeachingClassResponse | null>(null);
  const [exams, setExams] = useState<ExamResponse[]>([]);
  const [examsError, setExamsError] = useState<string | null>(null);
  const [isLoadingExams, setIsLoadingExams] = useState(false);
  const [selectedExamId, setSelectedExamId] = useState<number | null>(null);

  const [sheet, setSheet] = useState<MarksEntrySheetResponse | null>(null);
  const [sheetError, setSheetError] = useState<string | null>(null);
  const [isLoadingSheet, setIsLoadingSheet] = useState(false);

  const [rows, setRows] = useState<Record<number, RowState>>({});
  const [rowErrors, setRowErrors] = useState<Record<number, string>>({});
  const [saveResult, setSaveResult] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    setExams([]);
    setSelectedExamId(null);
    setSheet(null);
    setSaveResult(null);
    setSaveError(null);

    if (!selectedClass) return;

    let cancelled = false;
    setIsLoadingExams(true);
    setExamsError(null);
    examService
      .listExams({
        subjectId: selectedClass.subjectId,
        academicYear: selectedClass.academicYear,
        semester: selectedClass.semester,
        section: selectedClass.section,
        size: 100,
        sort: "examDate,desc",
      })
      .then((page) => {
        if (cancelled) return;
        setExams(page.content);
      })
      .catch((err) => {
        if (!cancelled) setExamsError(extractErrorMessage(err, "Failed to load exams for this class."));
      })
      .finally(() => {
        if (!cancelled) setIsLoadingExams(false);
      });

    return () => {
      cancelled = true;
    };
    // Re-runs only when the selected class actually changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedClass?.assignmentId]);

  async function loadSheet(examId: number) {
    setIsLoadingSheet(true);
    setSheetError(null);
    setSaveResult(null);
    setSaveError(null);
    try {
      const result = await marksService.getEntrySheet(examId);
      setSheet(result);
      const next: Record<number, RowState> = {};
      for (const entry of result.entries) {
        next[entry.studentId] = {
          marksObtained: entry.marksObtained === null ? "" : String(entry.marksObtained),
          remarks: entry.remarks ?? "",
        };
      }
      setRows(next);
      setRowErrors({});
    } catch (err) {
      setSheet(null);
      setSheetError(extractErrorMessage(err, "Failed to load the entry sheet."));
    } finally {
      setIsLoadingSheet(false);
    }
  }

  function selectExam(examId: number) {
    setSelectedExamId(examId);
    loadSheet(examId);
  }

  async function handleSubmit() {
    if (!sheet) return;
    setSaveError(null);
    setSaveResult(null);

    const errors: Record<number, string> = {};
    const entries: MarksEntry[] = [];

    for (const entry of sheet.entries) {
      const row = rows[entry.studentId];
      const raw = row?.marksObtained ?? "";
      const error = validateMark(raw, sheet.maximumMarks);
      if (error) {
        errors[entry.studentId] = error;
        continue;
      }
      if (raw.trim() === "") continue;
      const remarks = row?.remarks.trim();
      entries.push({ studentId: entry.studentId, marksObtained: Number(raw), remarks: remarks || null });
    }

    setRowErrors(errors);
    if (Object.keys(errors).length > 0) {
      setSaveError("Fix the highlighted marks before submitting.");
      return;
    }
    if (entries.length === 0) {
      setSaveError("Enter at least one student's marks before submitting.");
      return;
    }

    setIsSaving(true);
    try {
      const result = await marksService.saveBulk({ examId: sheet.examId, entries });
      setSaveResult(`Saved: ${result.createdCount} created, ${result.updatedCount} updated.`);
      await loadSheet(sheet.examId);
    } catch (err) {
      setSaveError(extractErrorMessage(err, "Failed to save marks."));
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Marks</h1>
        <p className="text-muted-foreground">Enter and review marks for your exams.</p>
      </div>

      <Card>
        <CardContent className="space-y-4 pt-6">
          <ClassScopePicker value={selectedClass} onChange={setSelectedClass} />

          {selectedClass && (
            <div className="space-y-1.5">
              <Label htmlFor="marks-exam">Exam</Label>
              {examsError && (
                <Alert variant="destructive">
                  <AlertDescription>{examsError}</AlertDescription>
                </Alert>
              )}
              {!examsError && isLoadingExams && (
                <p className="text-sm text-muted-foreground">Loading exams…</p>
              )}
              {!examsError && !isLoadingExams && exams.length === 0 && (
                <p className="text-sm text-muted-foreground">
                  No exams have been scheduled for this class yet. Create one on the Exams page first.
                </p>
              )}
              {!examsError && exams.length > 0 && (
                <Select
                  value={selectedExamId ? String(selectedExamId) : null}
                  onValueChange={(value) => value && selectExam(Number(value))}
                >
                  <SelectTrigger id="marks-exam" className="w-full">
                    <SelectValue placeholder="Select an exam" />
                  </SelectTrigger>
                  <SelectContent>
                    {exams.map((exam) => (
                      <SelectItem key={exam.id} value={String(exam.id)}>
                        {exam.title} — {EXAM_TYPE_LABELS[exam.examType]} · {exam.examDate} (max{" "}
                        {exam.maximumMarks})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {selectedClass && selectedExamId && (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <CardTitle>{sheet?.examTitle ?? "Entry sheet"}</CardTitle>
                <CardDescription>
                  {sheet
                    ? `${sheet.subjectCode} — ${sheet.subjectName} · Max marks ${sheet.maximumMarks} · ${sheet.enteredCount} of ${sheet.studentCount} entered`
                    : "Loading…"}
                </CardDescription>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => selectedExamId && loadSheet(selectedExamId)}
                disabled={isLoadingSheet}
              >
                <RefreshCwIcon />
                {isLoadingSheet ? "Loading…" : "Refresh"}
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {sheetError && (
              <Alert variant="destructive">
                <AlertDescription>{sheetError}</AlertDescription>
              </Alert>
            )}
            {saveError && (
              <Alert variant="destructive">
                <AlertDescription>{saveError}</AlertDescription>
              </Alert>
            )}
            {saveResult && (
              <Alert>
                <AlertDescription>{saveResult}</AlertDescription>
              </Alert>
            )}

            {sheet && (
              <>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Register no.</TableHead>
                      <TableHead>Student</TableHead>
                      <TableHead className="w-32">
                        Marks <span className="text-muted-foreground">(/ {sheet.maximumMarks})</span>
                      </TableHead>
                      <TableHead>Remarks</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {sheet.entries.map((entry) => (
                      <TableRow key={entry.studentId}>
                        <TableCell className="font-medium">{entry.registerNumber ?? "—"}</TableCell>
                        <TableCell>{entry.studentName}</TableCell>
                        <TableCell>
                          <Input
                            type="number"
                            min={0}
                            max={sheet.maximumMarks}
                            step={0.01}
                            value={rows[entry.studentId]?.marksObtained ?? ""}
                            aria-invalid={!!rowErrors[entry.studentId]}
                            onChange={(event) =>
                              setRows((prev) => ({
                                ...prev,
                                [entry.studentId]: {
                                  marksObtained: event.target.value,
                                  remarks: prev[entry.studentId]?.remarks ?? "",
                                },
                              }))
                            }
                          />
                          {rowErrors[entry.studentId] && (
                            <p className="text-xs text-destructive">{rowErrors[entry.studentId]}</p>
                          )}
                        </TableCell>
                        <TableCell>
                          <Input
                            value={rows[entry.studentId]?.remarks ?? ""}
                            maxLength={255}
                            placeholder="Optional"
                            onChange={(event) =>
                              setRows((prev) => ({
                                ...prev,
                                [entry.studentId]: {
                                  marksObtained: prev[entry.studentId]?.marksObtained ?? "",
                                  remarks: event.target.value,
                                },
                              }))
                            }
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>

                <div className="flex justify-end">
                  <Button type="button" onClick={handleSubmit} disabled={isSaving}>
                    {isSaving ? "Saving…" : "Save marks"}
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
