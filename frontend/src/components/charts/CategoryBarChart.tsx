import { Bar } from "react-chartjs-2";
import type { ChartOptions, TooltipItem } from "chart.js";

import { cn } from "@/lib/utils";

import { chartChrome, seriesColor } from "./chartColors";
import { registerCharts } from "./registerCharts";

registerCharts();

export interface CategoryBarChartProps {
  labels: string[];
  data: (number | null)[];
  colors?: string[];
  datasetLabel: string;
  horizontal?: boolean;
  yMax?: number;
  className?: string;
}

/**
 * A single-series bar chart keyed by category (subject, semester, grade letter, …).
 * A `null` value renders as an empty bar with a "—" tooltip rather than a zero-height
 * bar that reads as "actually measured zero" (§69).
 */
export function CategoryBarChart({
  labels,
  data,
  colors,
  datasetLabel,
  horizontal = false,
  yMax,
  className,
}: CategoryBarChartProps) {
  const chartData = {
    labels,
    datasets: [
      {
        label: datasetLabel,
        data,
        backgroundColor: colors ?? seriesColor(0),
        borderRadius: 6,
        maxBarThickness: 44,
      },
    ],
  };

  const chrome = chartChrome();

  const options: ChartOptions<"bar"> = {
    indexAxis: horizontal ? "y" : "x",
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: "top", labels: { color: chrome.tick, usePointStyle: true, boxWidth: 8 } },
      tooltip: {
        callbacks: {
          label: (item: TooltipItem<"bar">) => {
            const value = horizontal ? item.parsed.x : item.parsed.y;
            const text = value === null || value === undefined ? "—" : String(value);
            return `${item.dataset.label ?? ""}: ${text}`;
          },
        },
      },
    },
    scales: {
      [horizontal ? "x" : "y"]: {
        beginAtZero: true,
        max: yMax,
        grid: { color: chrome.grid },
        border: { color: chrome.border },
        ticks: { color: chrome.tick },
      },
      [horizontal ? "y" : "x"]: {
        grid: { display: false },
        border: { color: chrome.border },
        ticks: { color: chrome.tick },
      },
    },
  };

  return (
    <div className={cn("h-72 w-full", className)}>
      <Bar data={chartData} options={options} />
    </div>
  );
}

export default CategoryBarChart;
