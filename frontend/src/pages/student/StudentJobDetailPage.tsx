import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeftIcon, BuildingIcon, CalendarIcon, MapPinIcon } from "lucide-react";

import { ApplicationStatusBadge } from "@/components/placement/ApplicationStatusBadge";
import { EligibilityPanel } from "@/components/placement/EligibilityPanel";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import * as applicationService from "@/services/applicationService";
import * as jobService from "@/services/jobService";
import { listMyResumes } from "@/services/resumeService";
import { extractErrorMessage } from "@/utils/apiError";
import type { JobEligibilityResponse, JobResponse, JobType, PlacementApplicationResponse } from "@/types/placement";
import type { ResumeSummaryResponse } from "@/types/resume";

/** Sentinel `<Select>` value for "apply with no resume attached" (resumeId: null). */
const NO_RESUME = "__NONE__";

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
 * `/student/jobs/:jobId` — full drive detail, the eligibility verdict, and the apply
 * action. Applying and withdrawing both re-fetch eligibility so the panel always
 * reflects the true current state (e.g. ALREADY_APPLIED appearing right after a
 * successful apply) rather than a stale client-side guess.
 */
export default function StudentJobDetailPage() {
  const { jobId } = useParams<{ jobId: string }>();
  const id = Number(jobId);

  const [job, setJob] = useState<JobResponse | null>(null);
  const [eligibility, setEligibility] = useState<JobEligibilityResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const [coverNote, setCoverNote] = useState("");
  const [isApplying, setIsApplying] = useState(false);
  const [applyError, setApplyError] = useState<string | null>(null);
  const [createdApplication, setCreatedApplication] = useState<PlacementApplicationResponse | null>(null);

  const [resumes, setResumes] = useState<ResumeSummaryResponse[]>([]);
  const [resumesError, setResumesError] = useState<string | null>(null);
  const [selectedResumeId, setSelectedResumeId] = useState<string>(NO_RESUME);

  useEffect(() => {
    // The applicant's own resumes, offered as an optional attachment on the apply
    // form below. Preselect the first one — the API already sorts by updatedAt DESC,
    // so that's the most recently touched resume.
    listMyResumes({ size: 20 })
      .then((page) => {
        setResumes(page.content);
        if (page.content.length > 0) {
          setSelectedResumeId(String(page.content[0].id));
        }
      })
      .catch((err) => setResumesError(extractErrorMessage(err, "Failed to load your resumes.")));
  }, []);

  function load() {
    if (!id) return;
    setIsLoading(true);
    setLoadError(null);
    Promise.all([jobService.getJob(id), jobService.getJobEligibility(id)])
      .then(([jobResult, eligibilityResult]) => {
        setJob(jobResult);
        setEligibility(eligibilityResult);
      })
      .catch((err) => setLoadError(extractErrorMessage(err, "Failed to load this drive.")))
      .finally(() => setIsLoading(false));
  }

  useEffect(load, [id]);

  async function handleApply(event: FormEvent) {
    event.preventDefault();
    setApplyError(null);
    setIsApplying(true);
    try {
      const application = await applicationService.applyToJob({
        jobId: id,
        resumeId: selectedResumeId === NO_RESUME ? null : Number(selectedResumeId),
        coverNote: coverNote.trim() || null,
      });
      setCreatedApplication(application);
      setCoverNote("");
      // Re-fetch eligibility so the panel reflects ALREADY_APPLIED immediately.
      const refreshed = await jobService.getJobEligibility(id);
      setEligibility(refreshed);
    } catch (err) {
      setApplyError(extractErrorMessage(err, "Failed to submit your application."));
    } finally {
      setIsApplying(false);
    }
  }

  if (isLoading) {
    return <p className="py-8 text-center text-muted-foreground">Loading…</p>;
  }

  if (loadError || !job || !eligibility) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{loadError ?? "This drive could not be found."}</AlertDescription>
      </Alert>
    );
  }

  const salary = formatSalary(job);

  return (
    <div className="space-y-4">
      <Link
        to="/student/jobs"
        className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeftIcon className="size-3.5" />
        Back to drives
      </Link>

      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-semibold tracking-tight">{job.title}</h1>
          <Badge variant="outline">{JOB_TYPE_LABELS[job.jobType]}</Badge>
        </div>
        <p className="mt-1 flex items-center gap-1 text-muted-foreground">
          <BuildingIcon className="size-4" />
          {job.companyName}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
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
          {job.driveDate && <span>Drive date: {job.driveDate}</span>}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>About this role</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              {job.description ? (
                <p className="whitespace-pre-wrap">{job.description}</p>
              ) : (
                <p className="text-muted-foreground">No description provided.</p>
              )}
              {salary && (
                <p>
                  <span className="text-muted-foreground">Salary: </span>
                  {salary}
                </p>
              )}
              {job.openings !== null && (
                <p>
                  <span className="text-muted-foreground">Openings: </span>
                  {job.openings}
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Eligibility</CardTitle>
            </CardHeader>
            <CardContent>
              <EligibilityPanel eligibility={eligibility} />
            </CardContent>
          </Card>
        </div>

        <div>
          <Card>
            <CardHeader>
              <CardTitle>Apply</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {createdApplication ? (
                <div className="space-y-2 text-sm">
                  <p className="font-medium text-emerald-700 dark:text-emerald-400">
                    Application submitted.
                  </p>
                  <p className="text-muted-foreground">
                    Applied on {new Date(createdApplication.appliedAt).toLocaleString()}
                  </p>
                  <p>
                    {createdApplication.resumeTitle ? (
                      <>
                        Resume attached: <strong>{createdApplication.resumeTitle}</strong>
                      </>
                    ) : (
                      <span className="text-muted-foreground">No resume attached.</span>
                    )}
                  </p>
                  <ApplicationStatusBadge status={createdApplication.status} />
                </div>
              ) : eligibility.existingApplicationId !== null && eligibility.existingApplicationStatus ? (
                <div className="space-y-2 text-sm">
                  <p className="text-muted-foreground">You have already applied to this drive.</p>
                  <ApplicationStatusBadge status={eligibility.existingApplicationStatus} />
                </div>
              ) : (
                <form onSubmit={handleApply} className="space-y-3">
                  {applyError && (
                    <Alert variant="destructive">
                      <AlertDescription>{applyError}</AlertDescription>
                    </Alert>
                  )}
                  <div className="space-y-1.5">
                    <Label htmlFor="resume-select">Resume (optional)</Label>
                    {resumesError && <p className="text-xs text-destructive">{resumesError}</p>}
                    {!resumesError && resumes.length === 0 ? (
                      <p className="text-xs text-muted-foreground">
                        You haven&rsquo;t built a resume yet.{" "}
                        <Link to="/student/resumes/new" className="underline">
                          Create one
                        </Link>{" "}
                        to attach it to this application.
                      </p>
                    ) : (
                      !resumesError && (
                        <Select
                          value={selectedResumeId}
                          onValueChange={(value) => value && setSelectedResumeId(value)}
                        >
                          <SelectTrigger id="resume-select" className="w-full">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value={NO_RESUME}>No resume</SelectItem>
                            {resumes.map((resume) => (
                              <SelectItem key={resume.id} value={String(resume.id)}>
                                {resume.title}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      )
                    )}
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="cover-note">Cover note (optional)</Label>
                    <textarea
                      id="cover-note"
                      className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                      value={coverNote}
                      maxLength={2000}
                      onChange={(event) => setCoverNote(event.target.value)}
                      placeholder="Anything you'd like to add to your application…"
                    />
                  </div>
                  <Button type="submit" className="w-full" disabled={!eligibility.canApply || isApplying}>
                    {isApplying ? "Submitting…" : "Apply now"}
                  </Button>
                  {!eligibility.canApply && (
                    <p className="text-xs text-muted-foreground">
                      You can&rsquo;t apply right now — see the eligibility panel for why.
                    </p>
                  )}
                </form>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
