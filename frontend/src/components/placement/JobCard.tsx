import { CalendarIcon, CheckCircle2Icon, CircleAlertIcon, MapPinIcon, XCircleIcon } from "lucide-react";
import { Link } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { JobEligibilityResponse, JobResponse, JobType } from "@/types/placement";

const JOB_TYPE_LABELS: Record<JobType, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  INTERNSHIP: "Internship",
  CONTRACT: "Contract",
};

function formatSalary(job: JobResponse): string | null {
  if (job.salaryMin === null && job.salaryMax === null) return null;
  const fmt = (v: number) => v.toLocaleString();
  if (job.salaryMin !== null && job.salaryMax !== null) {
    return `${job.salaryCurrency} ${fmt(job.salaryMin)} – ${fmt(job.salaryMax)}`;
  }
  if (job.salaryMin !== null) return `${job.salaryCurrency} ${fmt(job.salaryMin)}+`;
  return `Up to ${job.salaryCurrency} ${fmt(job.salaryMax!)}`;
}

/**
 * A single drive card for the student browse page. `eligibility` is fetched per-job by
 * the parent (the eligibility endpoint is per-job, not batchable) and may be `undefined`
 * while its own request is still in flight — that renders as a neutral "Checking…"
 * state rather than silently guessing.
 */
export function JobCard({
  job,
  eligibility,
  eligibilityLoading = false,
}: {
  job: JobResponse;
  eligibility?: JobEligibilityResponse | null;
  eligibilityLoading?: boolean;
}) {
  const salary = formatSalary(job);

  return (
    <Card className="flex h-full flex-col">
      <CardHeader className="space-y-1">
        <div className="flex items-start justify-between gap-2">
          <div>
            <Link to={`/student/jobs/${job.id}`} className="font-semibold hover:underline">
              {job.title}
            </Link>
            <p className="text-sm text-muted-foreground">{job.companyName}</p>
          </div>
          <Badge variant="outline">{JOB_TYPE_LABELS[job.jobType]}</Badge>
        </div>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-3">
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
          {job.location && (
            <span className="flex items-center gap-1">
              <MapPinIcon className="size-3.5" />
              {job.location}
            </span>
          )}
          <span className="flex items-center gap-1">
            <CalendarIcon className="size-3.5" />
            Apply by {new Date(job.applicationDeadline).toLocaleString()}
          </span>
        </div>

        {salary && <p className="text-sm font-medium">{salary}</p>}

        <div className="flex flex-wrap gap-1.5 text-xs text-muted-foreground">
          {job.minCgpa !== null && <Badge variant="secondary">Min CGPA {job.minCgpa}</Badge>}
          {job.minMarksPercentage !== null && (
            <Badge variant="secondary">Min {job.minMarksPercentage}%</Badge>
          )}
          {job.graduationYear !== null && <Badge variant="secondary">Batch {job.graduationYear}</Badge>}
          {job.eligibleDepartments.length > 0 ? (
            <Badge variant="secondary">{job.eligibleDepartments.length} dept(s) eligible</Badge>
          ) : (
            <Badge variant="secondary">Open to all departments</Badge>
          )}
        </div>

        <div className="mt-auto pt-2">
          <EligibilityPill eligibility={eligibility} loading={eligibilityLoading} />
        </div>
      </CardContent>
    </Card>
  );
}

function EligibilityPill({
  eligibility,
  loading,
}: {
  eligibility?: JobEligibilityResponse | null;
  loading: boolean;
}) {
  if (loading || eligibility === undefined) {
    return <p className="text-xs text-muted-foreground">Checking eligibility…</p>;
  }
  if (eligibility === null) {
    return <p className="text-xs text-muted-foreground">Eligibility unavailable.</p>;
  }
  if (eligibility.canApply) {
    return (
      <p className="flex items-center gap-1.5 text-xs font-medium text-emerald-700 dark:text-emerald-400">
        <CheckCircle2Icon className="size-3.5" />
        You can apply
      </p>
    );
  }
  if (eligibility.eligible) {
    return (
      <p className="flex items-center gap-1.5 text-xs font-medium text-amber-700 dark:text-amber-400">
        <CircleAlertIcon className="size-3.5" />
        Meets criteria, but can&rsquo;t apply right now
      </p>
    );
  }
  const count = eligibility.reasons.length;
  return (
    <p className={cn("flex items-center gap-1.5 text-xs font-medium text-rose-700 dark:text-rose-400")}>
      <XCircleIcon className="size-3.5" />
      Not eligible ({count} reason{count === 1 ? "" : "s"})
    </p>
  );
}

export default JobCard;
