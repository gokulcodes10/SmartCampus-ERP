import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  BarChart3Icon,
  CalendarClockIcon,
  ClipboardCheckIcon,
  ClipboardListIcon,
  NotebookPenIcon,
} from "lucide-react";

import { EmptyChartState } from "@/components/analytics/EmptyChartState";
import { StatTile } from "@/components/analytics/StatTile";
import { DistributionDoughnutChart } from "@/components/charts/DistributionDoughnutChart";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";
import * as analyticsService from "@/services/analyticsService";
import * as examService from "@/services/examService";
import type { AnalyticsClassResponse } from "@/types/analytics";
import type { ExamResponse } from "@/types/academicOps";
import { extractErrorMessage } from "@/utils/apiError";

const UPCOMING_EXAM_LIMIT = 5;

const TEACHING_LINKS = [
  {
    to: "/faculty/attendance",
    label: "Attendance",
    description: "Mark attendance for the classes you teach",
    icon: ClipboardCheckIcon,
  },
  {
    to: "/faculty/exams",
    label: "Exams",
    description: "Schedule and manage exams",
    icon: ClipboardListIcon,
  },
  {
    to: "/faculty/marks",
    label: "Marks",
    description: "Enter and review student marks",
    icon: NotebookPenIcon,
  },
];

/**
 * Real data only (§69 — no fake functionality): navigation into the Phase 4 entry
 * screens plus the caller's own upcoming scheduled exams (assignment-scoped, from a
 * real backend endpoint). No invented counts (roster sizes, pending entries) are
 * shown here since there is no single real endpoint that sources them for a dashboard tile.
 */
export default function FacultyDashboardPage() {
  const { user } = useAuth();

  const [upcomingExams, setUpcomingExams] = useState<ExamResponse[] | null>(null);
  const [examsError, setExamsError] = useState<string | null>(null);
  const [examsLoading, setExamsLoading] = useState(true);

  const [analytics, setAnalytics] = useState<AnalyticsClassResponse | null>(null);
  const [analyticsError, setAnalyticsError] = useState<string | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(true);

  useEffect(() => {
    examService
      .listUpcoming(UPCOMING_EXAM_LIMIT)
      .then(setUpcomingExams)
      .catch((err) => setExamsError(extractErrorMessage(err, "Failed to load upcoming exams.")))
      .finally(() => setExamsLoading(false));

    // No filters = every class this faculty is assigned to (AcademicAccessGuard
    // scopes this server-side; a faculty with no assignments gets a real, all-zero
    // response, not an error — see the honest empty state below).
    analyticsService
      .getClassAnalytics({})
      .then(setAnalytics)
      .catch((err) => setAnalyticsError(extractErrorMessage(err, "Failed to load your analytics.")))
      .finally(() => setAnalyticsLoading(false));
  }, []);

  const hasAssignments = analytics != null && analytics.studentCount > 0;
  const classificationHasData = analytics ? analytics.classificationDistribution.some((s) => s.studentCount > 0) : false;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Welcome, {user?.fullName}</h1>
        <p className="text-muted-foreground">Faculty dashboard</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Teaching</CardTitle>
          <CardDescription>Enter attendance and marks for the classes you are assigned to.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-2 sm:grid-cols-1">
          {TEACHING_LINKS.map(({ to, label, description, icon: Icon }) => (
            <Link
              key={to}
              to={to}
              className="flex items-start gap-3 rounded-lg border border-border p-3 text-sm transition-colors hover:bg-muted"
            >
              <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <span>
                <span className="block font-medium">{label}</span>
                <span className="block text-muted-foreground">{description}</span>
              </span>
            </Link>
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2 text-base">
                <BarChart3Icon className="size-4 text-muted-foreground" />
                Analytics
              </CardTitle>
              <CardDescription>Attendance, marks and performance across your classes.</CardDescription>
            </div>
            <Button variant="outline" size="sm" render={<Link to="/faculty/analytics" />}>
              Full dashboard
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {analyticsLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {analyticsError && (
            <Alert variant="destructive">
              <AlertDescription>{analyticsError}</AlertDescription>
            </Alert>
          )}
          {!analyticsLoading && !analyticsError && !hasAssignments && (
            <p className="text-sm text-muted-foreground">
              You are not assigned to any class yet. Analytics will appear here once an
              administrator assigns you to a subject, section and semester.
            </p>
          )}
          {!analyticsLoading && !analyticsError && hasAssignments && analytics && (
            <>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <StatTile label="Attendance" value={analytics.attendancePercentage} suffix="%" emptyText="No classes held yet" />
                <StatTile label="Marks" value={analytics.marksPercentage} suffix="%" emptyText="No marks entered yet" />
                <StatTile label="Average GPA" value={analytics.averageGpa} emptyText="Not enough data" />
                <StatTile label="Students" value={analytics.studentCount} emptyText="0" />
              </div>
              {classificationHasData ? (
                <DistributionDoughnutChart
                  labels={analytics.classificationDistribution.map((s) => s.category)}
                  data={analytics.classificationDistribution.map((s) => s.studentCount)}
                  colors={analytics.classificationDistribution.map((s) => s.colorHex)}
                  className="max-w-xs"
                />
              ) : (
                <EmptyChartState message="No students have enough data to be classified yet." />
              )}
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClockIcon className="size-4 text-muted-foreground" />
            Upcoming exams
          </CardTitle>
          <CardDescription>Scheduled exams for your assigned classes.</CardDescription>
        </CardHeader>
        <CardContent>
          {examsLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {examsError && (
            <Alert variant="destructive">
              <AlertDescription>{examsError}</AlertDescription>
            </Alert>
          )}
          {!examsLoading && !examsError && upcomingExams?.length === 0 && (
            <p className="text-sm text-muted-foreground">No upcoming exams scheduled.</p>
          )}
          {!examsLoading && !examsError && upcomingExams && upcomingExams.length > 0 && (
            <ul className="space-y-2">
              {upcomingExams.map((exam) => (
                <li
                  key={exam.id}
                  className="flex items-center justify-between border-b border-border py-2 text-sm last:border-0"
                >
                  <span>
                    <span className="font-medium">{exam.title}</span>{" "}
                    <span className="text-muted-foreground">
                      ({exam.subjectCode} — {exam.subjectName}, {exam.section})
                    </span>
                  </span>
                  <span className="text-muted-foreground">{exam.examDate}</span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
