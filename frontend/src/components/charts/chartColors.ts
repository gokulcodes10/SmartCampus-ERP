/**
 * The single source of chart colour for the app.
 *
 * Chart.js paints to a canvas, so it cannot read Tailwind classes — it needs literal
 * colour strings. Rather than let every screen invent its own hex (which is how the
 * app ended up with three competing palettes), each value here resolves the same
 * `--chart-*` / status tokens the rest of the UI uses, at call time, so charts follow
 * the active theme instead of drifting from it.
 */

const FALLBACK_SERIES = ["#4f5bd5", "#16a374", "#e0a12b", "#6d4de6", "#3b82f6", "#c3cbdc"];

/** Reads a CSS custom property off `<html>`; returns `fallback` when unresolvable. */
function token(name: string, fallback: string): string {
  if (typeof window === "undefined") return fallback;
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

/** The categorical ramp, in order. Use it when series have no inherent meaning. */
export function seriesPalette(): string[] {
  return FALLBACK_SERIES.map((fallback, i) => token(`--chart-${i + 1}`, fallback));
}

/** The nth categorical colour, cycling once the ramp is exhausted. */
export function seriesColor(index: number): string {
  const palette = seriesPalette();
  return palette[index % palette.length];
}

/** One colour per item, for a distribution chart of arbitrary length. */
export function seriesColors(count: number): string[] {
  return Array.from({ length: count }, (_, i) => seriesColor(i));
}

/**
 * Colours that carry meaning rather than identity — use these when a value is good,
 * borderline or bad, and never for plain categories.
 */
export function statusColors() {
  return {
    success: token("--success-foreground", "#16a374"),
    warning: token("--warning-foreground", "#b5780f"),
    danger: token("--destructive", "#e04848"),
    info: token("--info-foreground", "#3b82f6"),
    neutral: token("--chart-6", "#c3cbdc"),
  };
}

/** Axis, gridline and legend colours, so charts sit correctly on either ground. */
export function chartChrome() {
  return {
    grid: token("--rule", "#eef0f5"),
    tick: token("--muted-foreground", "#6b7385"),
    border: token("--border", "#e3e7ef"),
  };
}
