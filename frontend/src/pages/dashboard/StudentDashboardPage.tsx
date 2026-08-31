import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { BarChart3Icon, CalendarClockIcon, ClipboardListIcon, GraduationCapIcon } from "lucide-react";

import { TrendLineChart } from "@/components/charts/TrendLineChart";
import { ClassificationBadge } from "@/components/analytics/ClassificationBadge";
import { UpcomingInterviewsCard } from "@/components/interview/UpcomingInterviewsCard";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";
import * as analyticsService from "@/services/analyticsService";
import * as examService from "@/services/examService";
import type { AnalyticsStudentResponse } from "@/types/analytics";
import type { ExamResponse } from "@/types/academicOps";
import { extractErrorMessage } from "@/utils/apiError";

const UPCOMING_EXAM_LIMIT = 5;

/**
 * Real data only (§69 — no fake functionality): the caller's own attendance,
 * marks and classification, all from one `GET /api/analytics/me` call, plus the
 * upcoming scheduled exams. There is no timetable module, so "upcoming" surfaces
 * exams, not classes (G6). Any call that fails or returns empty renders an honest
 * empty/error state rather than a 0 tile.
 */
export default function StudentDashboardPage() {
  const { user } = useAuth();

  const [analytics, setAnalytics] = useState<AnalyticsStudentResponse | null>(null);
  const [analyticsError, setAnalyticsError] = useState<string | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(true);

  const [upcomingExams, setUpcomingExams] = useState<ExamResponse[] | null>(null);
  const [examsError, setExamsError] = useState<string | null>(null);
  const [examsLoading, setExamsLoading] = useState(true);

  useEffect(() => {
    analyticsService
      .getMyAnalytics({})
      .then(setAnalytics)
      .catch((err) => setAnalyticsError(extractErrorMessage(err, "Failed to load your analytics.")))
      .finally(() => setAnalyticsLoading(false));

    examService
      .listUpcoming(UPCOMING_EXAM_LIMIT)
      .then(setUpcomingExams)
      .catch((err) => setExamsError(extractErrorMessage(err, "Failed to load upcoming exams.")))
      .finally(() => setExamsLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Welcome, {user?.fullName}</h1>
        <p className="text-muted-foreground">Student dashboard</p>
      </div>

      {analyticsError && (
        <Alert variant="destructive">
          <AlertDescription>{analyticsError}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <Link to="/student/attendance" className="block">
          <Card className="h-full transition-colors hover:bg-muted/50">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <ClipboardListIcon className="size-4 text-muted-foreground" />
                Attendance
              </CardTitle>
              <CardDescription>Your attendance across every subject.</CardDescription>
            </CardHeader>
            <CardContent>
              {analyticsLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
              {!analyticsLoading && analytics && (
                <div className="flex items-center gap-3">
                  <span className="text-2xl font-semibold tracking-tight">
                    {analytics.attendancePercentage === null
                      ? "No classes held"
                      : `${analytics.attendancePercentage.toFixed(2)}%`}
                  </span>
                  {analytics.attendance.lowAttendance && <Badge variant="destructive">Below minimum</Badge>}
                </div>
              )}
            </CardContent>
          </Card>
        </Link>

        <Link to="/student/marks" className="block">
          <Card className="h-full transition-colors hover:bg-muted/50">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <GraduationCapIcon className="size-4 text-muted-foreground" />
                CGPA
              </CardTitle>
              <CardDescription>Credit-weighted across every graded subject.</CardDescription>
            </CardHeader>
            <CardContent>
              {analyticsLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
              {!analyticsLoading && analytics && (
                <span className="text-2xl font-semibold tracking-tight">
                  {analytics.cgpa === null ? "Not graded yet" : analytics.cgpa}
                </span>
              )}
            </CardContent>
          </Card>
        </Link>
      </div>

      <Link to="/student/analytics" className="block">
        <Card className="transition-colors hover:bg-muted/50">
          <CardHeader>
            <CardTitle className="flex items-center justify-between gap-2 text-base">
              <span className="flex items-center gap-2">
                <BarChart3Icon className="size-4 text-muted-foreground" />
                Performance
              </span>
              {!analyticsLoading && analytics && (
                <ClassificationBadge
                  category={analytics.classification.category}
                  colorHex={analytics.classification.colorHex}
                  reason={analytics.classification.reason}
                />
              )}
            </CardTitle>
            <CardDescription>Attendance trend over the last {analytics?.trendMonths ?? "few"} months.</CardDescription>
          </CardHeader>
          <CardContent>
            {analyticsLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
            {!analyticsLoading && analytics && analytics.attendanceTrend.length > 0 && (
              <TrendLineChart
                className="h-24"
                labels={analytics.attendanceTrend.map((p) => p.period)}
                yMax={100}
                datasets={[
                  {
                    label: "Attendance %",
                    data: analytics.attendanceTrend.map((p) => p.attendancePercentage),
                    color: "#2563EB",
                  },
                ]}
              />
            )}
            {!analyticsLoading && analytics && analytics.attendanceTrend.length === 0 && (
              <p className="text-sm text-muted-foreground">No attendance recorded yet.</p>
            )}
            <p className="pt-2 text-sm text-muted-foreground">View the full analytics dashboard →</p>
          </CardContent>
        </Card>
      </Link>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <CalendarClockIcon className="size-4 text-muted-foreground" />
            Upcoming exams
          </CardTitle>
          <CardDescription>Scheduled exams for subjects you are enrolled in.</CardDescription>
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
                      ({exam.subjectCode} — {exam.subjectName})
                    </span>
                  </span>
                  <span className="text-muted-foreground">{exam.examDate}</span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <UpcomingInterviewsCard />
    </div>
  );
}
