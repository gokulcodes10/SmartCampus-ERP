import { cn } from "@/lib/utils";
import type { PerformanceCategory } from "@/types/analytics";

export interface ClassificationBadgeProps {
  category: PerformanceCategory | null;
  colorHex: string | null;
  reason?: string;
  className?: string;
}

const CATEGORY_LABEL: Record<PerformanceCategory, string> = {
  EXCELLENT: "Excellent",
  GOOD: "Good",
  AVERAGE: "Average",
  AT_RISK: "At risk",
};

/**
 * Renders a student's performance classification using the colour the BACKEND
 * supplied (`colorHex`, from the configurable `performance_bands` table) — never a
 * colour hard-coded per category here. A `null` category (not enough data, or no
 * band matched) renders a neutral "Not classified" badge instead of guessing or
 * defaulting to AT_RISK (§69).
 */
export function ClassificationBadge({ category, colorHex, reason, className }: ClassificationBadgeProps) {
  if (category === null || colorHex === null) {
    return (
      <span
        title={reason}
        className={cn(
          "inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 rounded-4xl border border-border px-2 py-0.5 text-xs font-medium text-muted-foreground",
          className,
        )}
      >
        Not classified
      </span>
    );
  }

  return (
    <span
      title={reason}
      className={cn(
        "inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 rounded-4xl border border-transparent px-2 py-0.5 text-xs font-medium text-white",
        className,
      )}
      style={{ backgroundColor: colorHex }}
    >
      {CATEGORY_LABEL[category]}
    </span>
  );
}

export default ClassificationBadge;
