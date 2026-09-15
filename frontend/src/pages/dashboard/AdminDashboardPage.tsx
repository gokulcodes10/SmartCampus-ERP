import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  BarChart3Icon,
  BuildingIcon,
  GraduationCapIcon,
  LibraryBigIcon,
  UserRoundCheckIcon,
  UsersRoundIcon,
} from "lucide-react";

import { EmptyChartState } from "@/components/analytics/EmptyChartState";
import { KpiCard } from "@/components/analytics/KpiCard";
import { StatTile } from "@/components/analytics/StatTile";
import { PageHeader } from "@/components/layout/PageHeader";
import { DistributionDoughnutChart } from "@/components/charts/DistributionDoughnutChart";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";
import * as analyticsService from "@/services/analyticsService";
import type { AnalyticsAdminResponse } from "@/types/analytics";
import { extractErrorMessage } from "@/utils/apiError";

/**
 * Real data only (§69 — no fake functionality): the authenticated user from
 * `GET /api/auth/me`, navigation into the Phase 3 academic-management screens, and an
 * institution-wide analytics summary from `GET /api/analytics/overview` with no
 * filters. Every figure below traces to that one response — nothing is invented.
 */
const MANAGEMENT_LINKS = [
  {
    to: "/admin/departments",
    label: "Departments",
    description: "Academic departments",
    icon: BuildingIcon,
  },
  {
    to: "/admin/courses",
    label: "Courses",
    description: "Programs per department",
    icon: LibraryBigIcon,
  },
  {
    to: "/admin/subjects",
    label: "Subjects",
    description: "Syllabus subjects per course",
    icon: GraduationCapIcon,
  },
  {
    to: "/admin/students",
    label: "Students",
    description: "Manage students, activate pending sign-ups (G1)",
    icon: UserRoundCheckIcon,
  },
  {
    to: "/admin/faculty",
    label: "Faculty",
    description: "Provision and manage staff accounts",
    icon: UsersRoundIcon,
  },
];

export default function AdminDashboardPage() {
  const { user } = useAuth();

  const [analytics, setAnalytics] = useState<AnalyticsAdminResponse | null>(null);
  const [analyticsError, setAnalyticsError] = useState<string | null>(null);
  const [analyticsLoading, setAnalyticsLoading] = useState(true);

  useEffect(() => {
    analyticsService
      .getOverview({})
      .then(setAnalytics)
      .catch((err) => setAnalyticsError(extractErrorMessage(err, "Failed to load institution analytics.")))
      .finally(() => setAnalyticsLoading(false));
  }, []);

  const classificationHasData = analytics ? analytics.classificationDistribution.some((s) => s.studentCount > 0) : false;

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <PageHeader
        title={`Welcome, ${user?.fullName ?? ""}`}
        description="Here's what's happening across the institution today."
      />

      {/* The design's headline row: four saturated tiles carrying the figures an
          administrator checks first, before any chart. */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          label="Total students"
          value={analytics?.totalStudents ?? null}
          emptyText="—"
          hint={analytics ? `${analytics.activeStudents} active` : undefined}
          icon={UserRoundCheckIcon}
          tone="emerald"
        />
        <KpiCard
          label="Total faculty"
          value={analytics?.totalFaculty ?? null}
          emptyText="—"
          hint={analytics ? `Across ${analytics.totalDepartments} departments` : undefined}
          icon={UsersRoundIcon}
          tone="violet"
        />
        <KpiCard
          label="Pending sign-ups"
          value={analytics?.pendingStudents ?? null}
          emptyText="—"
          hint="Awaiting activation"
          icon={UserRoundCheckIcon}
          tone="blue"
        />
        <KpiCard
          label="Students at risk"
          value={analytics ? analytics.atRiskStudents.length : null}
          emptyText="—"
          hint="Below the attendance or marks threshold"
          icon={BarChart3Icon}
          tone="red"
        />
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2 text-base">
                <BarChart3Icon className="size-4 text-muted-foreground" />
                Institution analytics
              </CardTitle>
              <CardDescription>Attendance, marks and performance across the institution.</CardDescription>
            </div>
            <Button variant="outline" size="sm" render={<Link to="/admin/analytics" />}>
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
          {!analyticsLoading && !analyticsError && analytics && (
            <>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
                <StatTile label="Departments" value={analytics.totalDepartments} emptyText="0" />
                <StatTile label="Attendance" value={analytics.attendancePercentage} suffix="%" emptyText="No classes held yet" />
                <StatTile label="Marks" value={analytics.marksPercentage} suffix="%" emptyText="No marks entered yet" />
                <StatTile label="Average GPA" value={analytics.averageGpa} emptyText="Not enough data" />
                <StatTile
                  label="At risk"
                  value={analytics.atRiskStudents.length}
                  tone={analytics.atRiskStudents.length > 0 ? "danger" : "default"}
                  emptyText="0"
                />
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
          <CardTitle>Account</CardTitle>
          <CardDescription>Your authenticated account details.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div className="flex justify-between border-b border-border py-2">
            <span className="text-muted-foreground">Email</span>
            <span>{user?.email}</span>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <span className="text-muted-foreground">Role</span>
            <span>{user?.role}</span>
          </div>
          <div className="flex justify-between py-2">
            <span className="text-muted-foreground">Account status</span>
            <span>{user?.enabled ? "Active" : "Disabled"}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Academic administration</CardTitle>
          <CardDescription>
            Manage the core academic structure and accounts (Phase 3).
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-2 sm:grid-cols-2">
          {MANAGEMENT_LINKS.map(({ to, label, description, icon: Icon }) => (
            <Link
              key={to}
              to={to}
              className="flex items-start gap-3 rounded-[10px] border border-border p-3.5 text-sm transition-colors hover:border-primary/40 hover:bg-accent"
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
    </div>
  );
}
