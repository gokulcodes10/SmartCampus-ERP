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
  positive: "text-success-foreground",
  warning: "text-warning-foreground",
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
    <div className={cn("rounded-[10px] bg-surface-subtle px-4 py-3.5", className)}>
      <p className="text-[12.5px] font-medium text-muted-foreground">{label}</p>
      <p
        className={cn(
          "mt-1 text-2xl font-bold tracking-tight tabular-nums",
          value === null ? "text-muted-foreground" : TONE_CLASSES[tone],
        )}
      >
        {display}
      </p>
      {hint && <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}

export default StatTile;
