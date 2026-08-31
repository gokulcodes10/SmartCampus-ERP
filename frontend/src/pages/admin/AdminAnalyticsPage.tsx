import { useEffect, useMemo, useState } from "react";
import { ArrowDownIcon, ArrowUpIcon, ArrowUpDownIcon } from "lucide-react";

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
import * as departmentService from "@/services/departmentService";
import type {
  AnalyticsAdminResponse,
  AnalyticsFilterOptionsResponse,
  AttendanceTrendPoint,
  CohortStudentRow,
  MarksTrendPoint,
} from "@/types/analytics";
import { extractErrorMessage } from "@/utils/apiError";

/** Fixed categorical order — never hue-cycled — for the grade-distribution slices,
 *  which (unlike performance-band classification) carry no backend color. */
const GRADE_PALETTE = [
  "#2a78d6",
  "#eb6834",
  "#1baf7a",
  "#eda100",
  "#e87ba4",
  "#008300",
  "#4a3aa7",
  "#e34948",
];

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

type SortKey = "registerNumber" | "studentName" | "marksPercentage";
type SortDir = "asc" | "desc";

function compareNullable(a: number | string | null, b: number | string | null): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
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
 * Institution-wide analytics against `GET /api/analytics/overview` (ADMIN). Every
 * figure — including the institution counters — comes straight from
 * `AnalyticsAdminResponse`; nothing is computed in the browser (§10).
 */
export default function AdminAnalyticsPage() {
  const [options, setOptions] = useState<AnalyticsFilterOptionsResponse | null>(null);
  const [optionsError, setOptionsError] = useState<string | null>(null);
  const [departments, setDepartments] = useState<{ id: number; name: string }[]>([]);

  const [filters, setFilters] = useState<AnalyticsFilters>({});
  const [data, setData] = useState<AnalyticsAdminResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [sortKey, setSortKey] = useState<SortKey>("marksPercentage");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  useEffect(() => {
    analyticsService
      .getFilterOptions()
      .then(setOptions)
      .catch((err) => setOptionsError(extractErrorMessage(err, "Failed to load filter options.")));
    departmentService
      .listAllDepartments()
      .then((depts) => setDepartments(depts.map((d) => ({ id: d.id, name: d.name }))))
      .catch((err) => setOptionsError((prev) => prev ?? extractErrorMessage(err, "Failed to load departments.")));
  }, []);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setLoadError(null);
    analyticsService
      .getOverview(filters)
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
  }, [
    filters.departmentId,
    filters.courseId,
    filters.subjectId,
    filters.academicYear,
    filters.semester,
    filters.section,
    filters.months,
  ]);

  const trend = useMemo(
    () => (data ? mergeTrends(data.attendanceTrend, data.marksTrend) : { labels: [], attendanceData: [], marksData: [] }),
    [data],
  );

  const sortedAtRisk = useMemo(
    () => (data ? sortStudents(data.atRiskStudents, sortKey, sortDir) : []),
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
  const gradeHasData = data ? data.gradeDistribution.some((s) => s.count > 0) : false;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Institution analytics</h1>
        <p className="text-muted-foreground">Attendance, marks and performance across the institution.</p>
      </div>

      {optionsError && (
        <Alert variant="destructive">
          <AlertDescription>{optionsError}</AlertDescription>
        </Alert>
      )}
      {loadError && (
        <Alert variant="destructive">
          <AlertDescription>{loadError}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardContent className="pt-6">
          <AnalyticsFilterBar
            value={filters}
            onChange={setFilters}
            options={options}
            showDepartment
            departments={departments}
          />
        </CardContent>
      </Card>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Institution</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <StatTile label="Total students" value={isLoading ? null : (data?.totalStudents ?? 0)} emptyText="0" />
          <StatTile label="Active students" value={isLoading ? null : (data?.activeStudents ?? 0)} emptyText="0" />
          <StatTile label="Pending students" value={isLoading ? null : (data?.pendingStudents ?? 0)} emptyText="0" tone="warning" />
          <StatTile label="Total faculty" value={isLoading ? null : (data?.totalFaculty ?? 0)} emptyText="0" />
          <StatTile label="Active faculty" value={isLoading ? null : (data?.activeFaculty ?? 0)} emptyText="0" />
          <StatTile label="Departments" value={isLoading ? null : (data?.totalDepartments ?? 0)} emptyText="0" />
          <StatTile label="Courses" value={isLoading ? null : (data?.totalCourses ?? 0)} emptyText="0" />
          <StatTile label="Subjects" value={isLoading ? null : (data?.totalSubjects ?? 0)} emptyText="0" />
          <StatTile label="Exams" value={isLoading ? null : (data?.totalExams ?? 0)} emptyText="0" />
        </div>
      </div>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Scoped to current filters</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <StatTile label="Attendance" value={data?.attendancePercentage ?? null} suffix="%" emptyText="No classes held yet" />
          <StatTile label="Marks" value={data?.marksPercentage ?? null} suffix="%" emptyText="No marks entered yet" />
          <StatTile label="Average GPA" value={data?.averageGpa ?? null} emptyText="Not enough data" />
          <StatTile label="Students in scope" value={isLoading ? null : (data?.studentCount ?? 0)} emptyText="0" />
          <StatTile
            label="Classified"
            value={isLoading ? null : (data?.classifiedCount ?? 0)}
            hint={data ? `${data.unclassifiedCount} not yet classifiable` : undefined}
            emptyText="0"
          />
        </div>
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
            <CardTitle>Attendance by department</CardTitle>
            <CardDescription>Percentage of held classes attended, per department.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data || data.departments.length === 0 ? (
              <EmptyChartState message="No department data in this scope yet." />
            ) : (
              <CategoryBarChart
                labels={data.departments.map((d) => d.departmentCode ?? "—")}
                data={data.departments.map((d) => d.attendancePercentage)}
                datasetLabel="Attendance %"
                yMax={100}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Marks by department</CardTitle>
            <CardDescription>Percentage of marks scored, per department.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data || data.departments.length === 0 ? (
              <EmptyChartState message="No department data in this scope yet." />
            ) : (
              <CategoryBarChart
                labels={data.departments.map((d) => d.departmentCode ?? "—")}
                data={data.departments.map((d) => d.marksPercentage)}
                datasetLabel="Marks %"
                yMax={100}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Performance by semester</CardTitle>
            <CardDescription>Attendance and marks percentage across academic-year/semester.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data || data.semesters.length === 0 ? (
              <EmptyChartState message="No semester data in this scope yet." />
            ) : (
              <TrendLineChart
                labels={data.semesters.map((s) => `${s.academicYear} S${s.semester}`)}
                datasets={[
                  {
                    label: "Attendance %",
                    data: data.semesters.map((s) => s.attendancePercentage),
                    color: "#2a78d6",
                  },
                  {
                    label: "Marks %",
                    data: data.semesters.map((s) => s.marksPercentage),
                    color: "#eb6834",
                  },
                ]}
                yLabel="%"
                yMax={100}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Grade distribution</CardTitle>
            <CardDescription>Subject-grade results across the configured grade bands.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data || !gradeHasData ? (
              <EmptyChartState message="No graded results in this scope yet." />
            ) : (
              <DistributionDoughnutChart
                labels={data.gradeDistribution.map((s) => s.grade)}
                data={data.gradeDistribution.map((s) => s.count)}
                colors={data.gradeDistribution.map((_, i) => GRADE_PALETTE[i % GRADE_PALETTE.length])}
              />
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>At-risk students</CardTitle>
          <CardDescription>
            Students classified AT_RISK by the configured performance bands, lowest marks first.
          </CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <SortHeader label="Register no." sortKeyName="registerNumber" />
                <SortHeader label="Student" sortKeyName="studentName" />
                <TableHead>Department</TableHead>
                <TableHead>Course</TableHead>
                <TableHead>Attendance</TableHead>
                <SortHeader label="Marks" sortKeyName="marksPercentage" />
                <TableHead>GPA</TableHead>
                <TableHead>Classification</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && sortedAtRisk.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                    No at-risk students in this scope.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                sortedAtRisk.map((row) => (
                  <TableRow key={row.studentId}>
                    <TableCell className="font-medium">{row.registerNumber ?? "—"}</TableCell>
                    <TableCell>{row.studentName}</TableCell>
                    <TableCell className="text-muted-foreground">{row.departmentName ?? "—"}</TableCell>
                    <TableCell className="text-muted-foreground">{row.courseName ?? "—"}</TableCell>
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
    </div>
  );
}
