import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { ApplicationStatus } from "@/types/placement";

/** Colored pill for a placement `ApplicationStatus`. Terminal statuses (SELECTED,
 *  REJECTED, WITHDRAWN) get a visually distinct treatment from the in-flight ones so a
 *  glance at a table column tells you whether a row is still moving. */
const META: Record<ApplicationStatus, { label: string; className: string }> = {
  APPLIED: {
    label: "Applied",
    className: "border-border bg-muted text-muted-foreground",
  },
  UNDER_REVIEW: {
    label: "Under review",
    className: "border-sky-500/30 bg-sky-500/10 text-sky-700 dark:text-sky-400",
  },
  SHORTLISTED: {
    label: "Shortlisted",
    className: "border-indigo-500/30 bg-indigo-500/10 text-indigo-700 dark:text-indigo-400",
  },
  INTERVIEW_SCHEDULED: {
    label: "Interview scheduled",
    className: "border-violet-500/30 bg-violet-500/10 text-violet-700 dark:text-violet-400",
  },
  SELECTED: {
    label: "Selected",
    className: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400",
  },
  REJECTED: {
    label: "Rejected",
    className: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
  },
  WITHDRAWN: {
    label: "Withdrawn",
    className: "border-border bg-muted text-muted-foreground",
  },
};

export function ApplicationStatusBadge({ status, className }: { status: ApplicationStatus; className?: string }) {
  const meta = META[status];
  return (
    <Badge variant="outline" className={cn(meta.className, className)}>
      {meta.label}
    </Badge>
  );
}

export default ApplicationStatusBadge;
