import type { LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

export type KpiTone = "emerald" | "violet" | "blue" | "red" | "navy";

const TONE_GRADIENT: Record<KpiTone, string> = {
  emerald: "var(--gradient-kpi-emerald)",
  violet: "var(--gradient-kpi-violet)",
  blue: "var(--gradient-kpi-blue)",
  red: "var(--gradient-kpi-red)",
  navy: "var(--gradient-kpi-navy)",
};

export interface KpiCardProps {
  label: string;
  /** `null` means "no data", and renders `emptyText` — never a misleading "0". */
  value: number | string | null;
  suffix?: string;
  emptyText?: string;
  hint?: string;
  icon?: LucideIcon;
  tone?: KpiTone;
  className?: string;
}

/**
 * The design's headline metric tile: a saturated gradient card carrying a label, a
 * large figure and a supporting line, with the metric's glyph in a translucent disc.
 *
 * Honest empty states are preserved from `StatTile` (§60/§69) — a `null` value shows
 * `emptyText` rather than a zero the data never measured.
 */
export function KpiCard({
  label,
  value,
  suffix,
  emptyText = "No data yet",
  hint,
  icon: Icon,
  tone = "violet",
  className,
}: KpiCardProps) {
  const isEmpty = value === null;
  const display = isEmpty
    ? emptyText
    : typeof value === "number" && suffix === "%"
      ? `${value.toFixed(2)}%`
      : `${value}${suffix ?? ""}`;

  return (
    <div
      className={cn(
        "flex min-h-[150px] flex-col justify-between rounded-lg px-6 py-5.5 text-white shadow-card",
        className,
      )}
      style={{ backgroundImage: TONE_GRADIENT[tone] }}
    >
      <div className="flex items-start justify-between gap-3">
        <span className="text-[13.5px] font-medium">{label}</span>
        {Icon && (
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-white/20">
            <Icon className="size-4" aria-hidden />
          </span>
        )}
      </div>
      <div>
        <div
          className={cn(
            "font-bold tracking-tight tabular-nums",
            isEmpty ? "text-lg leading-tight" : "text-[32px] leading-none",
          )}
        >
          {display}
        </div>
        {hint && <div className="mt-1 text-xs">{hint}</div>}
      </div>
    </div>
  );
}

export default KpiCard;
