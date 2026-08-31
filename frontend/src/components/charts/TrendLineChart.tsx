import { Line } from "react-chartjs-2";
import type { ChartOptions, TooltipItem } from "chart.js";

import { cn } from "@/lib/utils";

import { registerCharts } from "./registerCharts";

registerCharts();

export interface TrendLineChartDataset {
  label: string;
  data: (number | null)[];
  color: string;
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
  const data = {
    labels,
    datasets: datasets.map((ds) => ({
      label: ds.label,
      data: ds.data,
      borderColor: ds.color,
      backgroundColor: ds.color,
      pointBackgroundColor: ds.color,
      spanGaps: false,
      tension: 0.25,
      fill: false,
    })),
  };

  const options: ChartOptions<"line"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: "top" },
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
        title: yLabel ? { display: true, text: yLabel } : undefined,
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
