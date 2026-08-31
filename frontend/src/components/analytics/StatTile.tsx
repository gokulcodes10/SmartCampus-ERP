import { cn } from "@/lib/utils";

export interface StatTileProps {
  label: string;
  value: number | null;
  suffix?: string;
  emptyText?: string;
  hint?: string;
  tone?: "default" | "positive" | "warning" | "danger";
  className?: string;
}

const TONE_CLASSES: Record<NonNullable<StatTileProps["tone"]>, string> = {
  default: "text-foreground",
  positive: "text-emerald-600 dark:text-emerald-400",
  warning: "text-amber-600 dark:text-amber-400",
  danger: "text-destructive",
};

/**
 * A single stat figure with an honest empty state. A `null` value ALWAYS renders
 * `emptyText` — never "0" — because a null figure means "no denominator / no data",
 * not "measured as zero" (§60/§69).
 */
export function StatTile({
  label,
  value,
  suffix,
  emptyText = "No data yet",
  hint,
  tone = "default",
  className,
}: StatTileProps) {
  const display =
    value === null
      ? emptyText
      : suffix === "%"
        ? `${value.toFixed(2)}%`
        : `${value}${suffix ?? ""}`;

  return (
    <div className={cn("space-y-1", className)}>
      <p className="text-sm text-muted-foreground">{label}</p>
      <p
        className={cn(
          "text-2xl font-semibold tracking-tight",
          value === null ? "text-muted-foreground" : TONE_CLASSES[tone],
        )}
      >
        {display}
      </p>
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

export default StatTile;
