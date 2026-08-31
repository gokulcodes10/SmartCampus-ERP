import { useEffect, useMemo, useState } from "react";
import { ArrowDownIcon, ArrowUpIcon, ArrowUpDownIcon, InboxIcon } from "lucide-react";

import { AnalyticsFilterBar, type AnalyticsFilters } from "@/components/analytics/AnalyticsFilterBar";
import { ClassificationBadge } from "@/components/analytics/ClassificationBadge";
import { EmptyChartState } from "@/components/analytics/EmptyChartState";
import { StatTile } from "@/components/analytics/StatTile";
import { CategoryBarChart } from "@/components/charts/CategoryBarChart";
import { DistributionDoughnutChart } from "@/components/charts/DistributionDoughnutChart";
import { TrendLineChart } from "@/components/charts/TrendLineChart";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as analyticsService from "@/services/analyticsService";
import type {
  AnalyticsClassResponse,
  AnalyticsFilterOptionsResponse,
  AttendanceTrendPoint,
  CohortStudentRow,
  MarksTrendPoint,
} from "@/types/analytics";
import { extractErrorMessage } from "@/utils/apiError";

/** Union-merges the two trend series on their `period` label, in chronological order
 *  (by `periodStart`), inserting null — never 0 — where a series has no point for
 *  that period (§10). */
function mergeTrends(attendanceTrend: AttendanceTrendPoint[], marksTrend: MarksTrendPoint[]) {
  const periodStart = new Map<string, string>();
  for (const p of attendanceTrend) periodStart.set(p.period, p.periodStart);
  for (const p of marksTrend) if (!periodStart.has(p.period)) periodStart.set(p.period, p.periodStart);

  const labels = Array.from(periodStart.keys()).sort((a, b) =>
    periodStart.get(a)!.localeCompare(periodStart.get(b)!),
  );

  const attendanceByPeriod = new Map(attendanceTrend.map((p) => [p.period, p.attendancePercentage]));
  const marksByPeriod = new Map(marksTrend.map((p) => [p.period, p.marksPercentage]));

  return {
    labels,
    attendanceData: labels.map((l) => attendanceByPeriod.get(l) ?? null),
    marksData: labels.map((l) => marksByPeriod.get(l) ?? null),
  };
}

type SortKey = "registerNumber" | "studentName" | "attendancePercentage" | "marksPercentage" | "gpa";
type SortDir = "asc" | "desc";

function compareNullable(a: number | string | null, b: number | string | null): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1; // nulls last
  if (b == null) return -1;
  if (typeof a === "string" && typeof b === "string") return a.localeCompare(b);
  return (a as number) - (b as number);
}

function sortStudents(rows: CohortStudentRow[], key: SortKey, dir: SortDir): CohortStudentRow[] {
  const sorted = [...rows].sort((a, b) => compareNullable(a[key], b[key]));
  return dir === "asc" ? sorted : sorted.reverse();
}

function fmtPct(v: number | null): string {
  return v == null ? "—" : `${v}%`;
}

function fmtNum(v: number | null): string {
  return v == null ? "—" : String(v);
}

/**
 * Faculty analytics dashboard against `GET /api/analytics/class` — scoped server-side
 * to the caller's own assigned classes via `AcademicAccessGuard`. Every figure comes
 * straight from `AnalyticsClassResponse`; nothing is computed in the browser (§10).
 */
export default function FacultyAnalyticsPage() {
  const [options, setOptions] = useState<AnalyticsFilterOptionsResponse | null>(null);
  const [optionsError, setOptionsError] = useState<string | null>(null);

  const [filters, setFilters] = useState<AnalyticsFilters>({});
  const [data, setData] = useState<AnalyticsClassResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [sortKey, setSortKey] = useState<SortKey>("registerNumber");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  useEffect(() => {
    analyticsService
      .getFilterOptions()
      .then(setOptions)
      .catch((err) => setOptionsError(extractErrorMessage(err, "Failed to load filter options.")));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);
    analyticsService
      .getClassAnalytics(filters)
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setLoadError(extractErrorMessage(err, "Failed to load analytics."));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.courseId, filters.subjectId, filters.academicYear, filters.semester, filters.section, filters.months]);

  const noAssignments = options !== null && options.courses.length === 0 && options.subjects.length === 0;

  const trend = useMemo(
    () => (data ? mergeTrends(data.attendanceTrend, data.marksTrend) : { labels: [], attendanceData: [], marksData: [] }),
    [data],
  );

  const sortedStudents = useMemo(
    () => (data ? sortStudents(data.students, sortKey, sortDir) : []),
    [data, sortKey, sortDir],
  );

  function toggleSort(key: SortKey) {
    if (key === sortKey) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  }

  function SortHeader({ label, sortKeyName }: { label: string; sortKeyName: SortKey }) {
    const Icon = sortKey !== sortKeyName ? ArrowUpDownIcon : sortDir === "asc" ? ArrowUpIcon : ArrowDownIcon;
    return (
      <TableHead>
        <button
          type="button"
          className="flex items-center gap-1 font-medium hover:text-foreground"
          onClick={() => toggleSort(sortKeyName)}
        >
          {label}
          <Icon className="size-3.5 text-muted-foreground" />
        </button>
      </TableHead>
    );
  }

  const classificationHasData = data ? data.classificationDistribution.some((s) => s.studentCount > 0) : false;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Class analytics</h1>
          <p className="text-muted-foreground">
            Attendance, marks and performance across the classes you teach.
          </p>
        </div>
      </div>

      {optionsError && (
        <Alert variant="destructive">
          <AlertDescription>{optionsError}</AlertDescription>
        </Alert>
      )}

      {noAssignments ? (
        <Alert>
          <InboxIcon />
          <AlertDescription>
            You are not assigned to any class yet. Once an administrator assigns you to a subject,
            section and semester, analytics for that class will appear here.
          </AlertDescription>
        </Alert>
      ) : (
        <>
          <Card>
            <CardContent className="pt-6">
              <AnalyticsFilterBar value={filters} onChange={setFilters} options={options} />
            </CardContent>
          </Card>

          {loadError && (
            <Alert variant="destructive">
              <AlertDescription>{loadError}</AlertDescription>
            </Alert>
          )}

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            <StatTile label="Attendance" value={data?.attendancePercentage ?? null} suffix="%" emptyText="No classes held yet" />
            <StatTile label="Marks" value={data?.marksPercentage ?? null} suffix="%" emptyText="No marks entered yet" />
            <StatTile label="Average GPA" value={data?.averageGpa ?? null} emptyText="Not enough data" />
            <StatTile label="Students" value={isLoading ? null : (data?.studentCount ?? 0)} emptyText="0" />
            <StatTile
              label="Classified"
              value={isLoading ? null : (data?.classifiedCount ?? 0)}
              hint={data ? `${data.unclassifiedCount} not yet classifiable` : undefined}
              emptyText="0"
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Attendance &amp; marks trend</CardTitle>
                <CardDescription>Monthly percentage over the trend window.</CardDescription>
              </CardHeader>
              <CardContent>
                {trend.labels.length === 0 ? (
                  <EmptyChartState message="No attendance or marks recorded in this window yet." />
                ) : (
                  <TrendLineChart
                    labels={trend.labels}
                    datasets={[
                      { label: "Attendance %", data: trend.attendanceData, color: "#2a78d6" },
                      { label: "Marks %", data: trend.marksData, color: "#eb6834" },
                    ]}
                    yLabel="%"
                    yMax={100}
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Performance classification</CardTitle>
                <CardDescription>Students grouped by the configured performance bands.</CardDescription>
              </CardHeader>
              <CardContent>
                {!data || !classificationHasData ? (
                  <EmptyChartState message="No students have enough data to be classified yet." />
                ) : (
                  <DistributionDoughnutChart
                    labels={data.classificationDistribution.map((s) => s.category)}
                    data={data.classificationDistribution.map((s) => s.studentCount)}
                    colors={data.classificationDistribution.map((s) => s.colorHex)}
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Exam averages</CardTitle>
                <CardDescription>Average percentage scored per exam.</CardDescription>
              </CardHeader>
              <CardContent>
                {!data || data.examAverages.length === 0 ? (
                  <EmptyChartState message="No exams with entered marks in this scope yet." />
                ) : (
                  <CategoryBarChart
                    labels={data.examAverages.map((e) => e.title)}
                    data={data.examAverages.map((e) => e.averagePercentage)}
                    datasetLabel="Average %"
                    yMax={100}
                  />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Subject averages</CardTitle>
                <CardDescription>Marks percentage per subject in scope.</CardDescription>
              </CardHeader>
              <CardContent>
                {!data || data.subjectAverages.length === 0 ? (
                  <EmptyChartState message="No subject data in this scope yet." />
                ) : (
                  <CategoryBarChart
                    labels={data.subjectAverages.map((s) => s.subjectCode)}
                    data={data.subjectAverages.map((s) => s.marksPercentage)}
                    datasetLabel="Marks %"
                    yMax={100}
                  />
                )}
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Students</CardTitle>
              <CardDescription>
                {data ? `${data.students.length} student${data.students.length === 1 ? "" : "s"} in scope.` : "Loading…"}
              </CardDescription>
            </CardHeader>
            <CardContent className="px-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <SortHeader label="Register no." sortKeyName="registerNumber" />
                    <SortHeader label="Student" sortKeyName="studentName" />
                    <SortHeader label="Attendance" sortKeyName="attendancePercentage" />
                    <SortHeader label="Marks" sortKeyName="marksPercentage" />
                    <SortHeader label="GPA" sortKeyName="gpa" />
                    <TableHead>Classification</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {isLoading && (
                    <TableRow>
                      <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                        Loading…
                      </TableCell>
                    </TableRow>
                  )}
                  {!isLoading && sortedStudents.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                        No students in this scope yet.
                      </TableCell>
                    </TableRow>
                  )}
                  {!isLoading &&
                    sortedStudents.map((row) => (
                      <TableRow key={row.studentId}>
                        <TableCell className="font-medium">{row.registerNumber ?? "—"}</TableCell>
                        <TableCell>{row.studentName}</TableCell>
                        <TableCell>{fmtPct(row.attendancePercentage)}</TableCell>
                        <TableCell>{fmtPct(row.marksPercentage)}</TableCell>
                        <TableCell>{fmtNum(row.gpa)}</TableCell>
                        <TableCell>
                          <ClassificationBadge category={row.classification} colorHex={row.classificationColorHex} />
                        </TableCell>
                      </TableRow>
                    ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
