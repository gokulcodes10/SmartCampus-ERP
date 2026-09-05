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
import { fromDatetimeLocalValue, toDatetimeLocalValue } from "@/components/interview/interviewLabels";
import * as interviewService from "@/services/interviewService";
import type { InterviewResponse } from "@/types/interview";
import { extractErrorMessage } from "@/utils/apiError";

interface InterviewRescheduleDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  interview: InterviewResponse | null;
  onRescheduled: (interview: InterviewResponse) => void;
}

/**
 * `PUT /api/interviews/{id}/reschedule` — only valid from SCHEDULED/RESCHEDULED (the
 * page that opens this dialog is expected to only offer it for those statuses). On a
 * 409 CONFLICT the server names the clashing window; that message is surfaced verbatim,
 * never replaced with a generic "something went wrong".
 */
export function InterviewRescheduleDialog({
  open,
  onOpenChange,
  interview,
  onRescheduled,
}: InterviewRescheduleDialogProps) {
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  // Reset the form whenever the dialog opens (not in an effect — adjusted
  // during render, following https://react.dev/learn/you-might-not-need-an-effect).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open && interview) {
      setStart(toDatetimeLocalValue(interview.scheduledStart));
      setEnd(toDatetimeLocalValue(interview.scheduledEnd));
      setError(null);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!interview) return;
    setError(null);
    if (!start || !end) {
      setError("Both a start and end time are required.");
      return;
    }
    if (end <= start) {
      setError("End time must be after the start time.");
      return;
    }
    setIsSaving(true);
    try {
      const updated = await interviewService.rescheduleInterview(interview.id, {
        scheduledStart: fromDatetimeLocalValue(start),
        scheduledEnd: fromDatetimeLocalValue(end),
      });
      onRescheduled(updated);
      onOpenChange(false);
    } catch (err) {
      setError(extractErrorMessage(err, "Failed to reschedule this interview."));
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Reschedule interview</DialogTitle>
            <DialogDescription>
              {interview ? `"${interview.title}"` : ""} — sets status to Rescheduled. Overlaps with any of
              this student's other live interviews are rejected.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="rs-start">New start time</Label>
                <Input id="rs-start" type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="rs-end">New end time</Label>
                <Input id="rs-end" type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving ? "Saving…" : "Reschedule"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
