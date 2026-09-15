import { Doughnut } from "react-chartjs-2";
import type { ChartOptions, TooltipItem } from "chart.js";

import { cn } from "@/lib/utils";

import { chartChrome, seriesColors } from "./chartColors";
import { registerCharts } from "./registerCharts";

registerCharts();

export interface DistributionDoughnutChartProps {
  labels: string[];
  data: number[];
  /** Omit to use the design's categorical ramp. */
  colors?: string[];
  className?: string;
}

/** A share-of-total doughnut (grade distribution, classification mix, …). */
export function DistributionDoughnutChart({ labels, data, colors, className }: DistributionDoughnutChartProps) {
  const chrome = chartChrome();
  const chartData = {
    labels,
    datasets: [
      {
        data,
        backgroundColor: colors ?? seriesColors(data.length),
        borderColor: chrome.border,
        borderWidth: 2,
      },
    ],
  };

  const options: ChartOptions<"doughnut"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: "top", labels: { color: chrome.tick, usePointStyle: true, boxWidth: 8 } },
      tooltip: {
        callbacks: {
          label: (item: TooltipItem<"doughnut">) => {
            const value = item.parsed;
            const text = value === null || value === undefined ? "—" : String(value);
            return `${item.label ?? ""}: ${text}`;
          },
        },
      },
    },
  };

  return (
    <div className={cn("h-72 w-full", className)}>
      <Doughnut data={chartData} options={options} />
    </div>
  );
}

export default DistributionDoughnutChart;
