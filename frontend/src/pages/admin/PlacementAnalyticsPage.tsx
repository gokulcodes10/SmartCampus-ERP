import { useEffect, useState } from "react";

import { EmptyChartState } from "@/components/analytics/EmptyChartState";
import { StatTile } from "@/components/analytics/StatTile";
import { CategoryBarChart } from "@/components/charts/CategoryBarChart";
import { DistributionDoughnutChart } from "@/components/charts/DistributionDoughnutChart";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as placementAnalyticsService from "@/services/placementAnalyticsService";
import { extractErrorMessage } from "@/utils/apiError";
import type { PlacementAnalyticsResponse } from "@/types/placement";

/** Fixed categorical order for the application-status doughnut — never hue-cycled. */
const STATUS_PALETTE = [
  "#2a78d6",
  "#eb6834",
  "#4a3aa7",
  "#e87ba4",
  "#1baf7a",
  "#e34948",
  "#8a8f98",
];

/**
 * `/admin/placement/analytics` — institution-wide placement figures against
 * `GET /api/placement/analytics`. Every number comes straight from
 * `PlacementAnalyticsResponse`; nothing is computed in the browser (§10).
 * `placementRate` (overall and per-department) is `number | null` and renders
 * "Not enough data" when null — never a fabricated 0% (§69).
 */
export default function PlacementAnalyticsPage() {
  const [data, setData] = useState<PlacementAnalyticsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Runs once on mount only ([] deps): isLoading/error already start at their
    // reset values via useState above, so there is nothing to set synchronously here.
    let cancelled = false;
    placementAnalyticsService
      .getPlacementAnalytics()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, "Failed to load placement analytics."));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const statusHasData = data ? data.statusBreakdown.some((s) => s.count > 0) : false;
  const deptHasData = data ? data.departmentBreakdown.length > 0 : false;
  const funnelHasData = data ? data.jobFunnel.length > 0 : false;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Placement analytics</h1>
        <p className="text-muted-foreground">Companies, drives and applications across the institution.</p>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Companies &amp; drives</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          <StatTile label="Companies" value={isLoading ? null : (data?.totalCompanies ?? 0)} emptyText="0" />
          <StatTile label="Active companies" value={isLoading ? null : (data?.activeCompanies ?? 0)} emptyText="0" />
          <StatTile label="Total drives" value={isLoading ? null : (data?.totalJobs ?? 0)} emptyText="0" />
          <StatTile label="Open drives" value={isLoading ? null : (data?.openJobs ?? 0)} emptyText="0" tone="positive" />
          <StatTile label="Draft drives" value={isLoading ? null : (data?.draftJobs ?? 0)} emptyText="0" />
          <StatTile label="Closed / cancelled" value={isLoading ? null : ((data?.closedJobs ?? 0) + (data?.cancelledJobs ?? 0))} emptyText="0" />
        </div>
      </div>

      <div>
        <h2 className="mb-2 text-sm font-medium text-muted-foreground">Applications &amp; outcomes</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <StatTile label="Total applications" value={isLoading ? null : (data?.totalApplications ?? 0)} emptyText="0" />
          <StatTile label="Unique applicants" value={isLoading ? null : (data?.uniqueApplicants ?? 0)} emptyText="0" />
          <StatTile label="Selected students" value={isLoading ? null : (data?.selectedStudents ?? 0)} emptyText="0" tone="positive" />
          <StatTile label="Active students" value={isLoading ? null : (data?.activeStudents ?? 0)} emptyText="0" />
          <StatTile
            label="Placement rate"
            value={data?.placementRate ?? null}
            suffix="%"
            emptyText="Not enough data"
            tone="positive"
          />
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Application status breakdown</CardTitle>
            <CardDescription>Where every application currently stands.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data || !statusHasData ? (
              <EmptyChartState message="No applications have been submitted yet." />
            ) : (
              <DistributionDoughnutChart
                labels={data.statusBreakdown.map((s) => s.status)}
                data={data.statusBreakdown.map((s) => s.count)}
                colors={data.statusBreakdown.map((_, i) => STATUS_PALETTE[i % STATUS_PALETTE.length])}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Placement rate by department</CardTitle>
            <CardDescription>Selected students as a share of active students, per department.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data || !deptHasData ? (
              <EmptyChartState message="No department data yet." />
            ) : (
              <CategoryBarChart
                labels={data.departmentBreakdown.map((d) => d.departmentName)}
                data={data.departmentBreakdown.map((d) => d.placementRate)}
                datasetLabel="Placement rate %"
                yMax={100}
              />
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Applications by drive (top 10)</CardTitle>
            <CardDescription>Ranked by total applications received.</CardDescription>
          </CardHeader>
          <CardContent>
            {!data || !funnelHasData ? (
              <EmptyChartState message="No drives have received applications yet." />
            ) : (
              <CategoryBarChart
                labels={data.jobFunnel.map((j) => j.jobTitle)}
                data={data.jobFunnel.map((j) => j.applicationCount)}
                datasetLabel="Applications"
                horizontal
              />
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Drive funnel</CardTitle>
          <CardDescription>Applications, shortlists, selections and rejections per drive.</CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Drive</TableHead>
                <TableHead>Company</TableHead>
                <TableHead>Applications</TableHead>
                <TableHead>Shortlisted</TableHead>
                <TableHead>Selected</TableHead>
                <TableHead>Rejected</TableHead>
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
              {!isLoading && !funnelHasData && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    No drives yet.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.jobFunnel.map((row) => (
                  <TableRow key={row.jobId}>
                    <TableCell className="font-medium">{row.jobTitle}</TableCell>
                    <TableCell className="text-muted-foreground">{row.companyName}</TableCell>
                    <TableCell>{row.applicationCount}</TableCell>
                    <TableCell>{row.shortlistedCount}</TableCell>
                    <TableCell>{row.selectedCount}</TableCell>
                    <TableCell>{row.rejectedCount}</TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Top companies</CardTitle>
          <CardDescription>Ranked by students selected, then total applications.</CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Company</TableHead>
                <TableHead>Drives</TableHead>
                <TableHead>Applications</TableHead>
                <TableHead>Selected</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && (!data || data.topCompanies.length === 0) && (
                <TableRow>
                  <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                    No companies yet.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.topCompanies.map((row) => (
                  <TableRow key={row.companyId}>
                    <TableCell className="font-medium">{row.companyName}</TableCell>
                    <TableCell>{row.jobCount}</TableCell>
                    <TableCell>{row.applicationCount}</TableCell>
                    <TableCell>{row.selectedCount}</TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
