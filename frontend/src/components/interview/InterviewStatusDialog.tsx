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
import { OUTCOME_LABELS, STATUS_LABELS, STATUS_TRANSITIONS } from "@/components/interview/interviewLabels";
import * as interviewService from "@/services/interviewService";
import type { InterviewOutcome, InterviewResponse, InterviewStatus } from "@/types/interview";
import { extractErrorMessage } from "@/utils/apiError";

const OUTCOMES: InterviewOutcome[] = ["AWAITING_RESULT", "SELECTED", "REJECTED", "ON_HOLD"];
const NO_OUTCOME = "__NONE__";

interface InterviewStatusDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  interview: InterviewResponse | null;
  onUpdated: (interview: InterviewResponse) => void;
}

/**
 * `PUT /api/interviews/{id}/status` — the only place a status/outcome ever changes.
 * §7 rules enforced client-side before the request goes out, matching the backend
 * exactly: status=CANCELLED requires a non-blank cancellationReason; outcome may only
 * be set when status=COMPLETED; the status choices offered are exactly this
 * interview's allowed transitions (a terminal interview offers none).
 */
export function InterviewStatusDialog({ open, onOpenChange, interview, onUpdated }: InterviewStatusDialogProps) {
  const [status, setStatus] = useState<InterviewStatus | "">("");
  const [outcome, setOutcome] = useState<InterviewOutcome | "">("");
  const [feedback, setFeedback] = useState("");
  const [cancellationReason, setCancellationReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  // Reset the form whenever the dialog opens (not in an effect — adjusted
  // during render, following https://react.dev/learn/you-might-not-need-an-effect).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open && interview) {
      setStatus("");
      setOutcome(interview.outcome ?? "");
      setFeedback(interview.feedback ?? "");
      setCancellationReason(interview.cancellationReason ?? "");
      setError(null);
    }
  }

  const allowedNext = interview ? STATUS_TRANSITIONS[interview.status] : [];

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!interview) return;
    setError(null);
    if (!status) {
      setError("Choose a new status.");
      return;
    }
    if (status === "CANCELLED" && !cancellationReason.trim()) {
      setError("A cancellation reason is required.");
      return;
    }
    setIsSaving(true);
    try {
      const updated = await interviewService.updateInterviewStatus(interview.id, {
        status,
        outcome: status === "COMPLETED" && outcome ? outcome : null,
        feedback: feedback.trim() || null,
        cancellationReason: status === "CANCELLED" ? cancellationReason.trim() : null,
      });
      onUpdated(updated);
      onOpenChange(false);
    } catch (err) {
      setError(extractErrorMessage(err, "Failed to update this interview's status."));
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Update status</DialogTitle>
            <DialogDescription>
              {interview ? `"${interview.title}" — currently ${STATUS_LABELS[interview.status]}.` : ""}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}

            {allowedNext.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                This interview is in a terminal status and cannot be changed further.
              </p>
            ) : (
              <>
                <div className="space-y-1.5">
                  <Label htmlFor="st-status">New status</Label>
                  <Select value={status} onValueChange={(value) => value && setStatus(value as InterviewStatus)}>
                    <SelectTrigger id="st-status" className="w-full">
                      <SelectValue placeholder="Select a status" />
                    </SelectTrigger>
                    <SelectContent>
                      {allowedNext.map((s) => (
                        <SelectItem key={s} value={s}>
                          {STATUS_LABELS[s]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {status === "COMPLETED" && (
                  <div className="space-y-1.5">
                    <Label htmlFor="st-outcome">Outcome (optional)</Label>
                    <Select
                      value={outcome || NO_OUTCOME}
                      onValueChange={(value) =>
                        setOutcome(value === NO_OUTCOME ? "" : (value as InterviewOutcome))
                      }
                    >
                      <SelectTrigger id="st-outcome" className="w-full">
                        <SelectValue placeholder="No outcome yet" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NO_OUTCOME}>No outcome yet</SelectItem>
                        {OUTCOMES.map((o) => (
                          <SelectItem key={o} value={o}>
                            {OUTCOME_LABELS[o]}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}

                {status === "CANCELLED" && (
                  <div className="space-y-1.5">
                    <Label htmlFor="st-reason">Cancellation reason</Label>
                    <Input
                      id="st-reason"
                      value={cancellationReason}
                      maxLength={500}
                      onChange={(e) => setCancellationReason(e.target.value)}
                      placeholder="Required"
                    />
                  </div>
                )}

                <div className="space-y-1.5">
                  <Label htmlFor="st-feedback">Feedback (optional)</Label>
                  <textarea
                    id="st-feedback"
                    className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                    value={feedback}
                    onChange={(e) => setFeedback(e.target.value)}
                  />
                </div>
              </>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Close
            </Button>
            {allowedNext.length > 0 && (
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : "Update status"}
              </Button>
            )}
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
