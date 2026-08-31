import {
  ArcElement,
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  Filler,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
} from "chart.js";

/**
 * Registers every Chart.js controller/element/scale/plugin the Phase 5 chart
 * components use, exactly once. Chart.js 4 is tree-shakeable — nothing renders
 * until its pieces are registered — so every chart component in `components/charts`
 * calls this at module scope before rendering.
 */
let registered = false;

export function registerCharts(): void {
  if (registered) return;
  ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    BarElement,
    ArcElement,
    Tooltip,
    Legend,
    Filler,
  );
  registered = true;
}
