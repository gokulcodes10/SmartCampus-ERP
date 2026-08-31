import { Loader2Icon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { SubmissionStatus } from "@/types/coding";

/**
 * Colored pill for a `SubmissionStatus`. INTERNAL_ERROR is deliberately styled as a
 * distinct amber "system issue" tone — never green, never treated like a normal
 * verdict — because it means the judge could not be reached, not that the code was
 * wrong. See VerdictPanel for the fuller explanation shown alongside it.
 */
const META: Record<SubmissionStatus, { label: string; className: string; spin?: boolean }> = {
  PENDING: {
    label: "Pending",
    className: "border-border bg-muted text-muted-foreground",
    spin: true,
  },
  RUNNING: {
    label: "Running",
    className: "border-sky-500/30 bg-sky-500/10 text-sky-700 dark:text-sky-400",
    spin: true,
  },
  ACCEPTED: {
    label: "Accepted",
    className: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400",
  },
  WRONG_ANSWER: {
    label: "Wrong Answer",
    className: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
  },
  TIME_LIMIT_EXCEEDED: {
    label: "Time Limit Exceeded",
    className: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
  },
  MEMORY_LIMIT_EXCEEDED: {
    label: "Memory Limit Exceeded",
    className: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
  },
  COMPILATION_ERROR: {
    label: "Compilation Error",
    className: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
  },
  RUNTIME_ERROR: {
    label: "Runtime Error",
    className: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
  },
  INTERNAL_ERROR: {
    label: "Could Not Be Judged",
    className: "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400",
  },
};

export function StatusBadge({ status, className }: { status: SubmissionStatus; className?: string }) {
  const meta = META[status];
  return (
    <Badge variant="outline" className={cn(meta.className, "gap-1", className)}>
      {meta.spin && <Loader2Icon className="size-3 animate-spin" />}
      {meta.label}
    </Badge>
  );
}
