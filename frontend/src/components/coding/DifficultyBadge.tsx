import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { ProblemDifficulty } from "@/types/coding";

const META: Record<ProblemDifficulty, { label: string; className: string }> = {
  EASY: {
    label: "Easy",
    className:
      "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400",
  },
  MEDIUM: {
    label: "Medium",
    className: "border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-400",
  },
  HARD: {
    label: "Hard",
    className: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
  },
};

/** Colored pill for a `ProblemDifficulty`. */
export function DifficultyBadge({ difficulty, className }: { difficulty: ProblemDifficulty; className?: string }) {
  const meta = META[difficulty];
  return <Badge variant="outline" className={cn(meta.className, className)}>{meta.label}</Badge>;
}
