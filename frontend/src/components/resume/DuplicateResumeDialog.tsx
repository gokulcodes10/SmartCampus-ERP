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

/**
 * Shared by the resumes list (duplicate any saved version) and the editor (offered in
 * place of editing once a version is locked). The caller owns the actual
 * `duplicateResume` call and navigation on success — this only collects the new title.
 */
export function DuplicateResumeDialog({
  open,
  onOpenChange,
  sourceTitle,
  onConfirm,
  isSubmitting = false,
  error,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  sourceTitle: string;
  onConfirm: (title: string) => void;
  isSubmitting?: boolean;
  error?: string | null;
}) {
  const [title, setTitle] = useState("");
  // Reset the field whenever the dialog opens (not in an effect — adjusted
  // during render, following https://react.dev/learn/you-might-not-need-an-effect).
  const [prevOpen, setPrevOpen] = useState(open);
  if (open !== prevOpen) {
    setPrevOpen(open);
    if (open) setTitle(`${sourceTitle} (copy)`.slice(0, 150));
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) return;
    onConfirm(title.trim());
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Duplicate resume</DialogTitle>
            <DialogDescription>
              Creates an independent, unlocked copy of &ldquo;{sourceTitle}&rdquo; that you can keep editing.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5 py-2">
            <Label htmlFor="duplicate-title">New title</Label>
            <Input
              id="duplicate-title"
              value={title}
              maxLength={150}
              autoFocus
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <DialogFooter>
            <Button type="submit" disabled={isSubmitting || !title.trim()}>
              {isSubmitting ? "Duplicating…" : "Duplicate"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
