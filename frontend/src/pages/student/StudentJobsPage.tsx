import { useEffect, useMemo, useState } from "react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { JobCard } from "@/components/placement/JobCard";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useServerTable } from "@/hooks/useServerTable";
import * as jobService from "@/services/jobService";
import type { JobEligibilityResponse, JobType } from "@/types/placement";

const ALL_VALUE = "all";
const JOB_TYPES: JobType[] = ["FULL_TIME", "PART_TIME", "INTERNSHIP", "CONTRACT"];
const JOB_TYPE_LABELS: Record<JobType, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  INTERNSHIP: "Internship",
  CONTRACT: "Contract",
};

/**
 * `/student/jobs` — browsable list of currently open drives. Only `status: "OPEN"` is
 * requested; the backend forces any non-admin caller's status filter to {OPEN, CLOSED}
 * regardless, so this is a real (not merely cosmetic) filter, not a workaround.
 *
 * Eligibility is per-job (there is no batch endpoint), so it's fetched once per job
 * card actually on screen, after that page of drives has loaded.
 */
export default function StudentJobsPage() {
  const [jobTypeFilter, setJobTypeFilter] = useState<string>(ALL_VALUE);

  const filters = useMemo(() => {
    const f: { status: "OPEN"; jobType?: JobType } = { status: "OPEN" };
    if (jobTypeFilter !== ALL_VALUE) f.jobType = jobTypeFilter as JobType;
    return f;
  }, [jobTypeFilter]);

  const { data, isLoading, error, setPage, search, setSearch } = useServerTable(
    jobService.listJobs,
    filters,
    { sort: "applicationDeadline,asc", pageSize: 12 },
  );

  const [eligibilityByJob, setEligibilityByJob] = useState<Record<number, JobEligibilityResponse | null>>({});
  const [eligibilityLoading, setEligibilityLoading] = useState<Set<number>>(new Set());

  useEffect(() => {
    const jobs = data?.content ?? [];
    if (jobs.length === 0) return;
    const ids = jobs.map((j) => j.id);
    setEligibilityLoading(new Set(ids));
    let cancelled = false;

    Promise.allSettled(ids.map((id) => jobService.getJobEligibility(id))).then((results) => {
      if (cancelled) return;
      setEligibilityByJob((prev) => {
        const next = { ...prev };
        results.forEach((result, i) => {
          next[ids[i]] = result.status === "fulfilled" ? result.value : null;
        });
        return next;
      });
      setEligibilityLoading(new Set());
    });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Placement drives</h1>
        <p className="text-muted-foreground">Open job drives you may be eligible to apply to.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Open drives</CardTitle>
          <CardDescription>Search by title, or filter by job type.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search drives…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={jobTypeFilter} onValueChange={(value) => setJobTypeFilter(value ?? ALL_VALUE)}>
              <SelectTrigger className="w-40">
                <SelectValue placeholder="All types" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>All types</SelectItem>
                {JOB_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {JOB_TYPE_LABELS[t]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardHeader>
        <CardContent>
          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {isLoading && <p className="py-8 text-center text-muted-foreground">Loading…</p>}
          {!isLoading && data?.content.length === 0 && (
            <p className="py-8 text-center text-muted-foreground">No open drives right now — check back soon.</p>
          )}

          {!isLoading && data && data.content.length > 0 && (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {data.content.map((job) => (
                <JobCard
                  key={job.id}
                  job={job}
                  eligibility={eligibilityByJob[job.id]}
                  eligibilityLoading={eligibilityLoading.has(job.id)}
                />
              ))}
            </div>
          )}

          {data && (
            <PaginationBar
              page={data.page}
              size={data.size}
              totalElements={data.totalElements}
              totalPages={data.totalPages}
              onPageChange={setPage}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
