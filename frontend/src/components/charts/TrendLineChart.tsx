import { Line } from "react-chartjs-2";
import type { ChartOptions, TooltipItem } from "chart.js";

import { cn } from "@/lib/utils";

import { chartChrome, seriesColor } from "./chartColors";
import { registerCharts } from "./registerCharts";

registerCharts();

export interface TrendLineChartDataset {
  label: string;
  data: (number | null)[];
  /** Omit to take the next colour from the design's categorical ramp. */
  color?: string;
}

export interface TrendLineChartProps {
  labels: string[];
  datasets: TrendLineChartDataset[];
  yLabel?: string;
  yMax?: number;
  className?: string;
}

/**
 * A multi-series line chart for trend data (attendance %, marks %, GPA over time).
 * `spanGaps` is always `false` — a `null` point in a series renders as a visible gap,
 * never as an implied zero (the §69 rule, in chart form). A caller that merged two
 * series onto a shared period axis should pass `null`, not `0`, for a period a series
 * has no point for.
 */
export function TrendLineChart({ labels, datasets, yLabel, yMax, className }: TrendLineChartProps) {
  const chrome = chartChrome();
  const data = {
    labels,
    datasets: datasets.map((ds, i) => {
      const color = ds.color ?? seriesColor(i);
      return {
      label: ds.label,
      data: ds.data,
      borderColor: color,
      backgroundColor: color,
      pointBackgroundColor: color,
      spanGaps: false,
      tension: 0.25,
      fill: false,
      borderWidth: 2,
      pointRadius: 3,
      };
    }),
  };

  const options: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: "top", labels: { color: chrome.tick, usePointStyle: true, boxWidth: 8 } },
      tooltip: {
        callbacks: {
          label: (item: TooltipItem<"line">) => {
            const value = item.parsed.y;
            const text = value === null || value === undefined ? "—" : String(value);
            return `${item.dataset.label ?? ""}: ${text}`;
          },
        },
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        max: yMax,
        title: yLabel ? { display: true, text: yLabel, color: chrome.tick } : undefined,
        grid: { color: chrome.grid },
        border: { color: chrome.border },
        ticks: { color: chrome.tick },
      },
      x: {
        grid: { display: false },
        border: { color: chrome.border },
        ticks: { color: chrome.tick },
      },
    },
  };

  return (
    <div className={cn("h-72 w-full", className)}>
      <Line data={data} options={options} />
    </div>
  );
}

export default TrendLineChart;
