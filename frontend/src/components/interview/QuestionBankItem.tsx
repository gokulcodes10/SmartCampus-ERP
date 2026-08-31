import { useState } from "react";
import { BookmarkIcon, CheckCircle2Icon, ChevronDownIcon, ChevronUpIcon, Trash2Icon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  CATEGORY_LABELS,
  DIFFICULTY_BADGE_VARIANT,
  DIFFICULTY_LABELS,
} from "@/components/interview/interviewLabels";
import * as interviewQuestionService from "@/services/interviewQuestionService";
import type { InterviewQuestionResponse } from "@/types/interview";
import { extractErrorMessage } from "@/utils/apiError";

interface QuestionBankItemProps {
  question: InterviewQuestionResponse;
  onChanged: (updated: InterviewQuestionResponse) => void;
  /** Only offered for the caller's own AI-generated question. */
  onDelete?: (question: InterviewQuestionResponse) => void;
}

/**
 * One expandable question-bank row for StudentInterviewPrepPage. Mark-complete and
 * bookmark call `PUT .../progress` and re-render from the RESPONSE (which returns the
 * full updated question) — never optimistic local state, per the Phase 10 contract.
 */
export function QuestionBankItem({ question, onChanged, onDelete }: QuestionBankItemProps) {
  const [expanded, setExpanded] = useState(false);
  const [busy, setBusy] = useState<"complete" | "bookmark" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function toggleCompleted() {
    setBusy("complete");
    setError(null);
    try {
      const updated = await interviewQuestionService.updateProgress(question.id, {
        completed: !question.completed,
      });
      onChanged(updated);
    } catch (err) {
      setError(extractErrorMessage(err, "Failed to update progress."));
    } finally {
      setBusy(null);
    }
  }

  async function toggleBookmarked() {
    setBusy("bookmark");
    setError(null);
    try {
      const updated = await interviewQuestionService.updateProgress(question.id, {
        bookmarked: !question.bookmarked,
      });
      onChanged(updated);
    } catch (err) {
      setError(extractErrorMessage(err, "Failed to update bookmark."));
    } finally {
      setBusy(null);
    }
  }

  const tagList = (question.tags ?? "")
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean);

  return (
    <div className="rounded-lg border border-border p-3">
      <button
        type="button"
        className="flex w-full items-start justify-between gap-3 text-left"
        onClick={() => setExpanded((e) => !e)}
      >
        <div className="min-w-0 space-y-1.5">
          <div className="flex flex-wrap items-center gap-1.5">
            <Badge variant="outline">{CATEGORY_LABELS[question.category]}</Badge>
            <Badge variant={DIFFICULTY_BADGE_VARIANT[question.difficulty]}>
              {DIFFICULTY_LABELS[question.difficulty]}
            </Badge>
            {question.source === "AI_GENERATED" && <Badge variant="secondary">AI generated</Badge>}
            {question.mine && <Badge variant="outline">Mine</Badge>}
            {question.companyName && <Badge variant="outline">{question.companyName}</Badge>}
            {question.completed && (
              <Badge variant="secondary" className="gap-1">
                <CheckCircle2Icon className="size-3" /> Completed
              </Badge>
            )}
          </div>
          <p className="text-sm font-medium">{question.question}</p>
          {tagList.length > 0 && (
            <p className="text-xs text-muted-foreground">{tagList.join(" · ")}</p>
          )}
        </div>
        <div className="shrink-0 pt-1 text-muted-foreground">
          {expanded ? <ChevronUpIcon className="size-4" /> : <ChevronDownIcon className="size-4" />}
        </div>
      </button>

      {expanded && (
        <div className="mt-3 space-y-3 border-t border-border pt-3">
          {error && <p className="text-xs text-destructive">{error}</p>}

          {question.answer && (
            <div>
              <p className="text-xs font-medium text-muted-foreground">Answer</p>
              <p className="whitespace-pre-wrap text-sm">{question.answer}</p>
            </div>
          )}
          {question.explanation && (
            <div>
              <p className="text-xs font-medium text-muted-foreground">Explanation</p>
              <p className="whitespace-pre-wrap text-sm">{question.explanation}</p>
            </div>
          )}
          {!question.answer && !question.explanation && (
            <p className="text-sm text-muted-foreground">No answer or explanation recorded for this question.</p>
          )}

          <div className="flex flex-wrap items-center gap-2 pt-1">
            <Button
              type="button"
              size="sm"
              variant={question.completed ? "secondary" : "outline"}
              disabled={busy !== null}
              onClick={toggleCompleted}
            >
              <CheckCircle2Icon />
              {busy === "complete" ? "Saving…" : question.completed ? "Completed" : "Mark complete"}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={question.bookmarked ? "secondary" : "outline"}
              disabled={busy !== null}
              onClick={toggleBookmarked}
            >
              <BookmarkIcon />
              {busy === "bookmark" ? "Saving…" : question.bookmarked ? "Bookmarked" : "Bookmark"}
            </Button>
            {onDelete && question.mine && (
              <Button type="button" size="sm" variant="ghost" onClick={() => onDelete(question)}>
                <Trash2Icon />
                Delete
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
