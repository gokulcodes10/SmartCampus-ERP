import { Doughnut } from "react-chartjs-2";
import type { ChartOptions, TooltipItem } from "chart.js";

import { cn } from "@/lib/utils";

import { registerCharts } from "./registerCharts";

registerCharts();

export interface DistributionDoughnutChartProps {
  labels: string[];
  data: number[];
  colors: string[];
  className?: string;
}

/** A share-of-total doughnut (grade distribution, classification mix, …). */
export function DistributionDoughnutChart({ labels, data, colors, className }: DistributionDoughnutChartProps) {
  const chartData = {
    labels,
    datasets: [
      {
        data,
        backgroundColor: colors,
        borderWidth: 1,
      },
    ],
  };

  const options: ChartOptions<"doughnut"> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: "top" },
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
