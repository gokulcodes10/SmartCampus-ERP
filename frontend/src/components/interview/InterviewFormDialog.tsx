import { useState } from "react";
import type { FormEvent } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
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
import {
  INTERVIEW_TYPE_LABELS,
  MODE_LABELS,
  fromDatetimeLocalValue,
  toDatetimeLocalValue,
} from "@/components/interview/interviewLabels";
import * as interviewService from "@/services/interviewService";
import type { StudentResponse } from "@/types/academic";
import type {
  InterviewMode,
  InterviewResponse,
  InterviewScheduleRequest,
  InterviewType,
  InterviewUpdateRequest,
} from "@/types/interview";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const TYPES: InterviewType[] = ["TECHNICAL", "HR", "BEHAVIOURAL", "CODING", "APTITUDE", "MANAGERIAL", "MOCK"];
const MODES: InterviewMode[] = ["ONLINE", "ONSITE", "PHONE"];

const FORM_FIELDS = [
  "studentId",
  "title",
  "interviewType",
  "companyName",
  "roundName",
  "mode",
  "meetingLink",
  "location",
  "interviewerName",
  "scheduledStart",
  "scheduledEnd",
  "notes",
] as const;

interface FormState {
  studentId: string;
  title: string;
  interviewType: InterviewType;
  companyName: string;
  roundName: string;
  mode: InterviewMode;
  meetingLink: string;
  location: string;
  interviewerName: string;
  scheduledStart: string;
  scheduledEnd: string;
  notes: string;
}

function emptyForm(): FormState {
  return {
    studentId: "",
    title: "",
    interviewType: "TECHNICAL",
    companyName: "",
    roundName: "",
    mode: "ONLINE",
    meetingLink: "",
    location: "",
    interviewerName: "",
    scheduledStart: "",
    scheduledEnd: "",
    notes: "",
  };
}

function fromInterview(interview: InterviewResponse): FormState {
  return {
    studentId: String(interview.studentId),
    title: interview.title,
    interviewType: interview.interviewType,
    companyName: interview.companyName ?? "",
    roundName: interview.roundName ?? "",
    mode: interview.mode,
    meetingLink: interview.meetingLink ?? "",
    location: interview.location ?? "",
    interviewerName: interview.interviewerName ?? "",
    scheduledStart: toDatetimeLocalValue(interview.scheduledStart),
    scheduledEnd: toDatetimeLocalValue(interview.scheduledEnd),
    notes: interview.notes ?? "",
  };
}

interface InterviewFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** "create" posts InterviewScheduleRequest (with times); "edit" puts InterviewUpdateRequest (no times, no status). */
  mode: "create" | "edit";
  interview?: InterviewResponse | null;
  /** ADMIN callers must pick a student; STUDENT callers schedule for themselves (studentId omitted). */
  isAdmin: boolean;
  students?: StudentResponse[];
  studentsError?: string | null;
  onSaved: (interview: InterviewResponse) => void;
}

/**
 * Shared schedule/edit form for `/api/interviews`, used by both StudentInterviewsPage
 * and AdminInterviewsPage so the §7 client-side validation (ONLINE requires a meeting
 * link, end strictly after start) lives in exactly one place.
 */
export function InterviewFormDialog({
  open,
  onOpenChange,
  mode,
  interview,
  isAdmin,
  students,
  studentsError,
  onSaved,
}: InterviewFormDialogProps) {
  const [form, setForm] = useState<FormState>(emptyForm());
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  // Reset the form whenever the dialog opens (not in an effect — adjusted
  // during render, following https://react.dev/learn/you-might-not-need-an-effect).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) {
      setForm(mode === "edit" && interview ? fromInterview(interview) : emptyForm());
      setFieldErrors({});
      setFormError(null);
    }
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (isAdmin && mode === "create" && !form.studentId.trim()) {
      errors.studentId = "Select a student.";
    }
    if (!form.title.trim()) errors.title = "Title is required.";
    if (form.mode === "ONLINE" && !form.meetingLink.trim()) {
      errors.meetingLink = "A meeting link is required for an online interview.";
    }
    if (mode === "create") {
      if (!form.scheduledStart) errors.scheduledStart = "Start time is required.";
      if (!form.scheduledEnd) errors.scheduledEnd = "End time is required.";
      if (form.scheduledStart && form.scheduledEnd && form.scheduledEnd <= form.scheduledStart) {
        errors.scheduledEnd = "End time must be after the start time.";
      }
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    setIsSaving(true);
    try {
      let saved: InterviewResponse;
      if (mode === "create") {
        const payload: InterviewScheduleRequest = {
          studentId: isAdmin ? Number(form.studentId) : undefined,
          title: form.title.trim(),
          interviewType: form.interviewType,
          companyName: form.companyName.trim() || null,
          roundName: form.roundName.trim() || null,
          mode: form.mode,
          meetingLink: form.meetingLink.trim() || null,
          location: form.location.trim() || null,
          interviewerName: form.interviewerName.trim() || null,
          scheduledStart: fromDatetimeLocalValue(form.scheduledStart),
          scheduledEnd: fromDatetimeLocalValue(form.scheduledEnd),
          notes: form.notes.trim() || null,
        };
        saved = await interviewService.scheduleInterview(payload);
      } else {
        if (!interview) return;
        const payload: InterviewUpdateRequest = {
          title: form.title.trim(),
          interviewType: form.interviewType,
          companyName: form.companyName.trim() || null,
          roundName: form.roundName.trim() || null,
          mode: form.mode,
          meetingLink: form.meetingLink.trim() || null,
          location: form.location.trim() || null,
          interviewerName: form.interviewerName.trim() || null,
          notes: form.notes.trim() || null,
        };
        saved = await interviewService.updateInterview(interview.id, payload);
      }
      onSaved(saved);
      onOpenChange(false);
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, FORM_FIELDS);
        setFieldErrors((prev) => ({ ...prev, ...parsed.fieldErrors }));
        setFormError(parsed.formError ?? (Object.keys(parsed.fieldErrors).length === 0 ? raw : null));
      } else {
        setFormError(extractErrorMessage(err, "Failed to save this interview."));
      }
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>{mode === "create" ? "Schedule an interview" : "Edit interview"}</DialogTitle>
            <DialogDescription>
              {mode === "create"
                ? "Times are shown and saved in your local timezone."
                : "Times and status have their own controls."}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            {formError && (
              <Alert variant="destructive">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}

            {isAdmin && mode === "create" && (
              <div className="space-y-1.5">
                <Label htmlFor="iv-student">Student</Label>
                {studentsError && <p className="text-xs text-destructive">{studentsError}</p>}
                <Select
                  value={form.studentId}
                  onValueChange={(value) => value && setForm((f) => ({ ...f, studentId: value }))}
                >
                  <SelectTrigger id="iv-student" className="w-full" aria-invalid={!!fieldErrors.studentId}>
                    <SelectValue placeholder="Select a student" />
                  </SelectTrigger>
                  <SelectContent>
                    {(students ?? []).map((student) => (
                      <SelectItem key={student.id} value={String(student.id)}>
                        {(student.registerNumber ?? `#${student.id}`) + " — " + student.fullName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {fieldErrors.studentId && <p className="text-xs text-destructive">{fieldErrors.studentId}</p>}
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="iv-title">Title</Label>
              <Input
                id="iv-title"
                value={form.title}
                maxLength={200}
                onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                aria-invalid={!!fieldErrors.title}
                placeholder="e.g. Backend Engineer — Round 1"
              />
              {fieldErrors.title && <p className="text-xs text-destructive">{fieldErrors.title}</p>}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="iv-type">Interview type</Label>
                <Select
                  value={form.interviewType}
                  onValueChange={(value) => value && setForm((f) => ({ ...f, interviewType: value as InterviewType }))}
                >
                  <SelectTrigger id="iv-type" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TYPES.map((t) => (
                      <SelectItem key={t} value={t}>
                        {INTERVIEW_TYPE_LABELS[t]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="iv-mode">Mode</Label>
                <Select
                  value={form.mode}
                  onValueChange={(value) => value && setForm((f) => ({ ...f, mode: value as InterviewMode }))}
                >
                  <SelectTrigger id="iv-mode" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {MODES.map((m) => (
                      <SelectItem key={m} value={m}>
                        {MODE_LABELS[m]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="iv-company">Company (optional)</Label>
                <Input
                  id="iv-company"
                  value={form.companyName}
                  maxLength={150}
                  onChange={(e) => setForm((f) => ({ ...f, companyName: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="iv-round">Round (optional)</Label>
                <Input
                  id="iv-round"
                  value={form.roundName}
                  maxLength={100}
                  onChange={(e) => setForm((f) => ({ ...f, roundName: e.target.value }))}
                  placeholder="e.g. Round 1 — Screening"
                />
              </div>
            </div>

            {form.mode === "ONLINE" && (
              <div className="space-y-1.5">
                <Label htmlFor="iv-link">Meeting link</Label>
                <Input
                  id="iv-link"
                  value={form.meetingLink}
                  maxLength={500}
                  onChange={(e) => setForm((f) => ({ ...f, meetingLink: e.target.value }))}
                  aria-invalid={!!fieldErrors.meetingLink}
                  placeholder="https://…"
                />
                {fieldErrors.meetingLink && <p className="text-xs text-destructive">{fieldErrors.meetingLink}</p>}
              </div>
            )}

            {form.mode !== "ONLINE" && (
              <div className="space-y-1.5">
                <Label htmlFor="iv-location">Location (optional)</Label>
                <Input
                  id="iv-location"
                  value={form.location}
                  maxLength={255}
                  onChange={(e) => setForm((f) => ({ ...f, location: e.target.value }))}
                  placeholder={form.mode === "PHONE" ? "Phone number, if relevant" : "Venue / address"}
                />
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="iv-interviewer">Interviewer (optional)</Label>
              <Input
                id="iv-interviewer"
                value={form.interviewerName}
                maxLength={150}
                onChange={(e) => setForm((f) => ({ ...f, interviewerName: e.target.value }))}
              />
            </div>

            {mode === "create" && (
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="iv-start">Start time</Label>
                  <Input
                    id="iv-start"
                    type="datetime-local"
                    value={form.scheduledStart}
                    onChange={(e) => setForm((f) => ({ ...f, scheduledStart: e.target.value }))}
                    aria-invalid={!!fieldErrors.scheduledStart}
                  />
                  {fieldErrors.scheduledStart && (
                    <p className="text-xs text-destructive">{fieldErrors.scheduledStart}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="iv-end">End time</Label>
                  <Input
                    id="iv-end"
                    type="datetime-local"
                    value={form.scheduledEnd}
                    onChange={(e) => setForm((f) => ({ ...f, scheduledEnd: e.target.value }))}
                    aria-invalid={!!fieldErrors.scheduledEnd}
                  />
                  {fieldErrors.scheduledEnd && <p className="text-xs text-destructive">{fieldErrors.scheduledEnd}</p>}
                </div>
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="iv-notes">Notes (optional)</Label>
              <textarea
                id="iv-notes"
                className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                value={form.notes}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              />
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving ? "Saving…" : mode === "create" ? "Schedule" : "Save changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
