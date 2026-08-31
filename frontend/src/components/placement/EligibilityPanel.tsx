import { CheckCircle2Icon, CircleAlertIcon, InfoIcon, XCircleIcon } from "lucide-react";

import { ApplicationStatusBadge } from "@/components/placement/ApplicationStatusBadge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { ELIGIBILITY_BLOCKER_CODES, ELIGIBILITY_CRITERION_CODES } from "@/types/placement";
import type { EligibilityReason, JobEligibilityResponse } from "@/types/placement";

/**
 * Renders a `JobEligibilityResponse` in full: the overall verdict, and every entry in
 * `reasons` with its message plus its requirement/actual pair, verbatim — this panel
 * never invents, rewords or summarises a reason message (§34 checkpoint).
 *
 * `eligible` and `canApply` are different booleans and are rendered as different cases:
 *  - eligible && canApply  -> "you meet the criteria and can apply" (no reasons exist)
 *  - eligible && !canApply -> "you meet the criteria, but you can't apply right now"
 *    (a BLOCKER reason — deadline passed / drive not open / already applied)
 *  - !eligible             -> "you do not meet the criteria" (one or more CRITERION
 *    reasons), plus any BLOCKER reasons that also happen to apply, shown separately.
 */
export function EligibilityPanel({ eligibility }: { eligibility: JobEligibilityResponse }) {
  const criterionReasons = eligibility.reasons.filter((r) => ELIGIBILITY_CRITERION_CODES.includes(r.code));
  const blockerReasons = eligibility.reasons.filter((r) => ELIGIBILITY_BLOCKER_CODES.includes(r.code));

  return (
    <div className="space-y-4">
      {eligibility.eligible && eligibility.canApply && (
        <Alert className="border-emerald-500/30 bg-emerald-500/10 text-emerald-800 dark:text-emerald-300">
          <CheckCircle2Icon className="size-4" />
          <AlertTitle>You meet the criteria and can apply</AlertTitle>
          <AlertDescription className="text-emerald-800/80 dark:text-emerald-300/80">
            You meet every requirement for this drive and applications are currently open.
          </AlertDescription>
        </Alert>
      )}

      {eligibility.eligible && !eligibility.canApply && (
        <Alert className="border-amber-500/30 bg-amber-500/10 text-amber-800 dark:text-amber-300">
          <CircleAlertIcon className="size-4" />
          <AlertTitle>You meet the criteria, but you can&rsquo;t apply right now</AlertTitle>
          <AlertDescription className="text-amber-800/80 dark:text-amber-300/80">
            You satisfy every eligibility requirement for this drive, but applying is currently blocked.
          </AlertDescription>
        </Alert>
      )}

      {!eligibility.eligible && (
        <Alert variant="destructive">
          <XCircleIcon className="size-4" />
          <AlertTitle>You do not meet the criteria for this drive</AlertTitle>
          <AlertDescription>
            {criterionReasons.length} requirement{criterionReasons.length === 1 ? "" : "s"} not met — see below.
          </AlertDescription>
        </Alert>
      )}

      {criterionReasons.length > 0 && (
        <ReasonList
          heading="Requirements not met"
          reasons={criterionReasons}
          tone="destructive"
        />
      )}

      {blockerReasons.length > 0 && (
        <ReasonList
          heading="Why you can't apply right now"
          reasons={blockerReasons}
          tone="warning"
        />
      )}

      <div className="grid gap-3 rounded-lg border border-border p-3 sm:grid-cols-2">
        <CriterionRow
          label="Minimum CGPA"
          requirement={eligibility.minCgpa}
          actual={eligibility.studentCgpa}
        />
        <CriterionRow
          label="Minimum aggregate %"
          requirement={eligibility.minMarksPercentage}
          actual={eligibility.studentMarksPercentage}
          suffix="%"
        />
        {eligibility.requiredGraduationYear !== null && (
          <div className="space-y-0.5">
            <p className="text-xs text-muted-foreground">Required graduating batch</p>
            <p className="text-sm">
              {eligibility.requiredGraduationYear}
              <span className="text-muted-foreground">
                {" "}
                (you: {eligibility.studentGraduationYear ?? "unknown"})
              </span>
            </p>
          </div>
        )}
        {eligibility.eligibleDepartments.length > 0 && (
          <div className="space-y-0.5 sm:col-span-2">
            <p className="text-xs text-muted-foreground">Open to departments</p>
            <p className="text-sm">
              {eligibility.eligibleDepartments.map((d) => d.name).join(", ")}
              <span className="text-muted-foreground">
                {" "}
                (you: {eligibility.studentDepartmentName ?? "not set"})
              </span>
            </p>
          </div>
        )}
      </div>

      {eligibility.existingApplicationId !== null && eligibility.existingApplicationStatus !== null && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <InfoIcon className="size-4" />
          Your application status:
          <ApplicationStatusBadge status={eligibility.existingApplicationStatus} />
        </div>
      )}
    </div>
  );
}

function CriterionRow({
  label,
  requirement,
  actual,
  suffix = "",
}: {
  label: string;
  requirement: number | null;
  actual: number | null;
  suffix?: string;
}) {
  if (requirement === null) return null;
  return (
    <div className="space-y-0.5">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-sm">
        {requirement}
        {suffix}
        <span className="text-muted-foreground">
          {" "}
          (you: {actual === null ? "not available" : `${actual}${suffix}`})
        </span>
      </p>
    </div>
  );
}

function ReasonList({
  heading,
  reasons,
  tone,
}: {
  heading: string;
  reasons: EligibilityReason[];
  tone: "destructive" | "warning";
}) {
  return (
    <div className="space-y-2">
      <h3
        className={cn(
          "text-sm font-medium",
          tone === "destructive" ? "text-destructive" : "text-amber-700 dark:text-amber-400",
        )}
      >
        {heading}
      </h3>
      <ul className="space-y-2">
        {reasons.map((reason) => (
          <li
            key={reason.code}
            className={cn(
              "rounded-lg border p-2.5 text-sm",
              tone === "destructive"
                ? "border-destructive/30 bg-destructive/5"
                : "border-amber-500/30 bg-amber-500/5",
            )}
          >
            <p>{reason.message}</p>
            {(reason.requirement !== null || reason.actual !== null) && (
              <p className="mt-1 text-xs text-muted-foreground">
                {reason.requirement !== null && <>Requirement: {reason.requirement}</>}
                {reason.requirement !== null && reason.actual !== null && " · "}
                {reason.actual !== null && <>Your value: {reason.actual}</>}
              </p>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default EligibilityPanel;
