import { useEffect, useMemo, useState } from "react";

import { CategoryBarChart } from "@/components/charts/CategoryBarChart";
import { DistributionDoughnutChart } from "@/components/charts/DistributionDoughnutChart";
import { TrendLineChart } from "@/components/charts/TrendLineChart";
import { ClassificationBadge } from "@/components/analytics/ClassificationBadge";
import { EmptyChartState } from "@/components/analytics/EmptyChartState";
import { StatTile } from "@/components/analytics/StatTile";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as analyticsService from "@/services/analyticsService";
import type { AnalyticsStudentResponse } from "@/types/analytics";
import { extractErrorMessage } from "@/utils/apiError";

const ALL_SENTINEL = "__ALL__";

/** A chart-friendly categorical palette for grade letters, which the backend does not
 *  color (unlike performance categories, whose color always comes from the API). */
const GRADE_PALETTE = ["#2563EB", "#16A34A", "#CA8A04", "#DB2777", "#7C3AED", "#0891B2", "#EA580C", "#64748B"];

function formatPercent(value: number | null): string {
  return value === null ? "—" : `${value.toFixed(2)}%`;
}

function formatNumber(value: number | null): string {
  return value === null ? "—" : String(value);
}

/** Union of attendance/marks trend periods, merged onto one shared axis. A period
 *  present in only one series gets `null` (a gap), never `0`, on the other. */
function mergeTrends(data: AnalyticsStudentResponse) {
  const byPeriod = new Map<string, { periodStart: string; attendancePercentage: number | null; marksPercentage: number | null }>();
  for (const point of data.attendanceTrend) {
    byPeriod.set(point.period, {
      periodStart: point.periodStart,
      attendancePercentage: point.attendancePercentage,
      marksPercentage: null,
    });
  }
  for (const point of data.marksTrend) {
    const existing = byPeriod.get(point.period);
    if (existing) {
      existing.marksPercentage = point.marksPercentage;
    } else {
      byPeriod.set(point.period, {
        periodStart: point.periodStart,
        attendancePercentage: null,
        marksPercentage: point.marksPercentage,
      });
    }
  }
  const periods = Array.from(byPeriod.entries()).sort((a, b) => a[1].periodStart.localeCompare(b[1].periodStart));
  return {
    labels: periods.map(([period]) => period),
    attendance: periods.map(([, v]) => v.attendancePercentage),
    marks: periods.map(([, v]) => v.marksPercentage),
  };
}

/**
 * Student's own analytics dashboard, from `GET /api/analytics/me`. Every figure on
 * this page is a field echoed straight from the response — none is re-derived in the
 * browser (§69). A `null` figure always renders as an explicit empty state, never 0.
 */
export default function StudentAnalyticsPage() {
  const [academicYear, setAcademicYear] = useState<string>(ALL_SENTINEL);
  const [semester, setSemester] = useState<string>(ALL_SENTINEL);
  const [months, setMonths] = useState<string>("");

  const [data, setData] = useState<AnalyticsStudentResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    analyticsService
      .getMyAnalytics({
        academicYear: academicYear === ALL_SENTINEL ? undefined : academicYear,
        semester: semester === ALL_SENTINEL ? undefined : Number(semester),
        months: months.trim() ? Number(months) : undefined,
      })
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, "Failed to load your analytics."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [academicYear, semester, months]);

  // Selector options come from the student's own semester records — never a
  // hard-coded year list. Once loaded, they stay available even if a later
  // filtered fetch narrows `data.academics.semesters` down further.
  const [yearOptions, setYearOptions] = useState<string[]>([]);
  const [semesterOptions, setSemesterOptions] = useState<number[]>([]);
  useEffect(() => {
    if (!data) return;
    setYearOptions((prev) => {
      const years = new Set(prev);
      data.academics.semesters.forEach((s) => years.add(s.academicYear));
      return Array.from(years).sort();
    });
    setSemesterOptions((prev) => {
      const sems = new Set(prev);
      data.academics.semesters.forEach((s) => sems.add(s.semester));
      return Array.from(sems).sort((a, b) => a - b);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const trend = useMemo(() => (data ? mergeTrends(data) : null), [data]);

  const gradeChartSlices = useMemo(
    () => (data ? data.gradeDistribution.filter((slice) => slice.count > 0) : []),
    [data],
  );

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My analytics</h1>
        <p className="text-muted-foreground">
          Attendance, marks and grade trends, drawn entirely from your own academic record.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Leave a filter on "All" to see every academic year or semester.</CardDescription>
          <div className="flex flex-wrap items-end gap-3 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="filter-year">Academic year</Label>
              <Select value={academicYear} onValueChange={(v) => v && setAcademicYear(v)}>
                <SelectTrigger id="filter-year" className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_SENTINEL}>All years</SelectItem>
                  {yearOptions.map((year) => (
                    <SelectItem key={year} value={year}>
                      {year}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="filter-semester">Semester</Label>
              <Select value={semester} onValueChange={(v) => v && setSemester(v)}>
                <SelectTrigger id="filter-semester" className="w-32">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_SENTINEL}>All semesters</SelectItem>
                  {semesterOptions.map((sem) => (
                    <SelectItem key={sem} value={String(sem)}>
                      Sem {sem}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="filter-months">Trend window (months)</Label>
              <Input
                id="filter-months"
                type="number"
                min={1}
                placeholder="Default"
                value={months}
                onChange={(event) => setMonths(event.target.value)}
                className="w-28"
              />
            </div>
          </div>
        </CardHeader>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {loading && (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">Loading your analytics…</CardContent>
        </Card>
      )}

      {!loading && data && (
        <>
          <Card>
            <CardContent className="grid gap-6 py-6 sm:grid-cols-2 lg:grid-cols-4">
              <StatTile label="Attendance" value={data.attendancePercentage} suffix="%" />
              <StatTile label="Marks" value={data.marksPercentage} suffix="%" />
              <StatTile label="Current GPA" value={data.gpa} />
              <StatTile label="CGPA" value={data.cgpa} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Classification</CardTitle>
              <CardDescription>{data.classification.reason}</CardDescription>
            </CardHeader>
            <CardContent>
              <ClassificationBadge
                category={data.classification.category}
                colorHex={data.classification.colorHex}
                reason={data.classification.reason}
                className="px-3 py-1 text-sm"
              />
            </CardContent>
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Attendance &amp; marks trend</CardTitle>
                <CardDescription>Last {data.trendMonths} month(s), by attendance record month.</CardDescription>
              </CardHeader>
              <CardContent>
                {trend && trend.labels.length > 0 ? (
                  <TrendLineChart
                    labels={trend.labels}
                    yMax={100}
                    yLabel="%"
                    datasets={[
                      { label: "Attendance %", data: trend.attendance, color: "#2563EB" },
                      { label: "Marks %", data: trend.marks, color: "#16A34A" },
                    ]}
                  />
                ) : (
                  <EmptyChartState message="No attendance or marks records in this window yet." />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>GPA by semester</CardTitle>
                <CardDescription>Credit-weighted GPA for each graded semester.</CardDescription>
              </CardHeader>
              <CardContent>
                {data.gpaTrend.length > 0 ? (
                  <TrendLineChart
                    labels={data.gpaTrend.map((p) => `${p.academicYear} S${p.semester}`)}
                    yMax={10}
                    yLabel="GPA"
                    datasets={[
                      {
                        label: "GPA",
                        data: data.gpaTrend.map((p) => p.gpa),
                        color: "#7C3AED",
                      },
                    ]}
                  />
                ) : (
                  <EmptyChartState message="No graded semesters yet." />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Attendance % by subject</CardTitle>
              </CardHeader>
              <CardContent>
                {data.subjects.length > 0 ? (
                  <CategoryBarChart
                    labels={data.subjects.map((s) => s.subjectCode)}
                    data={data.subjects.map((s) => s.attendancePercentage)}
                    colors={data.subjects.map((s) => s.classificationColorHex ?? "#94A3B8")}
                    datasetLabel="Attendance %"
                    yMax={100}
                  />
                ) : (
                  <EmptyChartState message="No subjects to show yet." />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Marks % by subject</CardTitle>
              </CardHeader>
              <CardContent>
                {data.subjects.length > 0 ? (
                  <CategoryBarChart
                    labels={data.subjects.map((s) => s.subjectCode)}
                    data={data.subjects.map((s) => s.marksPercentage)}
                    colors={data.subjects.map((s) => s.classificationColorHex ?? "#94A3B8")}
                    datasetLabel="Marks %"
                    yMax={100}
                  />
                ) : (
                  <EmptyChartState message="No subjects to show yet." />
                )}
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-[minmax(0,320px)_1fr]">
            <Card>
              <CardHeader>
                <CardTitle>Grade distribution</CardTitle>
                <CardDescription>Across every subject grade you have.</CardDescription>
              </CardHeader>
              <CardContent>
                {gradeChartSlices.length > 0 ? (
                  <DistributionDoughnutChart
                    labels={gradeChartSlices.map((s) => s.grade)}
                    data={gradeChartSlices.map((s) => s.count)}
                    colors={gradeChartSlices.map((_, i) => GRADE_PALETTE[i % GRADE_PALETTE.length])}
                  />
                ) : (
                  <EmptyChartState message="No graded subjects yet." />
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Grade bands</CardTitle>
                <CardDescription>The full grading scale, including grades you have not earned yet.</CardDescription>
              </CardHeader>
              <CardContent className="px-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Grade</TableHead>
                      <TableHead>Grade point</TableHead>
                      <TableHead>Range</TableHead>
                      <TableHead>Your count</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {data.gradeDistribution.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                          No grade bands configured.
                        </TableCell>
                      </TableRow>
                    )}
                    {data.gradeDistribution.map((slice) => (
                      <TableRow key={slice.grade}>
                        <TableCell className="font-medium">{slice.grade}</TableCell>
                        <TableCell>{slice.gradePoint}</TableCell>
                        <TableCell>
                          {slice.minPercentage}% – {slice.maxPercentage}%
                        </TableCell>
                        <TableCell>{slice.count}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Subjects</CardTitle>
              <CardDescription>Attendance and marks per subject, with each subject's own classification.</CardDescription>
            </CardHeader>
            <CardContent className="px-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Subject</TableHead>
                    <TableHead>Year / Sem</TableHead>
                    <TableHead>Attendance</TableHead>
                    <TableHead>Marks</TableHead>
                    <TableHead>Grade</TableHead>
                    <TableHead>Grade point</TableHead>
                    <TableHead>Pass</TableHead>
                    <TableHead>Classification</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.subjects.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={8} className="py-8 text-center text-muted-foreground">
                        No subjects recorded yet.
                      </TableCell>
                    </TableRow>
                  )}
                  {data.subjects.map((subject) => (
                    <TableRow key={`${subject.subjectId}-${subject.academicYear}-${subject.semester}`}>
                      <TableCell className="font-medium">
                        {subject.subjectCode} — {subject.subjectName}
                      </TableCell>
                      <TableCell>
                        {subject.academicYear} / Sem {subject.semester}
                      </TableCell>
                      <TableCell>{formatPercent(subject.attendancePercentage)}</TableCell>
                      <TableCell>{formatPercent(subject.marksPercentage)}</TableCell>
                      <TableCell>{subject.grade ?? "—"}</TableCell>
                      <TableCell>{formatNumber(subject.gradePoint)}</TableCell>
                      <TableCell>
                        {subject.passed === null ? (
                          <span className="text-muted-foreground">—</span>
                        ) : subject.passed ? (
                          <Badge variant="default">Pass</Badge>
                        ) : (
                          <Badge variant="destructive">Fail</Badge>
                        )}
                      </TableCell>
                      <TableCell>
                        <ClassificationBadge
                          category={subject.classification}
                          colorHex={subject.classificationColorHex}
                        />
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
