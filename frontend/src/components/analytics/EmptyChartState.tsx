import { BarChart3Icon } from "lucide-react";

import { cn } from "@/lib/utils";

export interface EmptyChartStateProps {
  message: string;
  className?: string;
}

/**
 * Stands in for a chart when its dataset is empty, so an empty chart never reads
 * as a broken one (§69) — a blank Chart.js canvas gives no indication of why.
 */
export function EmptyChartState({ message, className }: EmptyChartStateProps) {
  return (
    <div
      className={cn(
        "flex h-72 w-full flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border text-center text-sm text-muted-foreground",
        className,
      )}
    >
      <BarChart3Icon className="size-6" />
      <p>{message}</p>
    </div>
  );
}

export default EmptyChartState;
