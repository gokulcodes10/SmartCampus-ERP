import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CalendarClockIcon } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { InterviewStatusBadge } from "@/components/interview/StatusBadge";
import { formatDateTime, INTERVIEW_TYPE_LABELS } from "@/components/interview/interviewLabels";
import * as interviewService from "@/services/interviewService";
import type { InterviewResponse } from "@/types/interview";
import { extractErrorMessage } from "@/utils/apiError";

const LIMIT = 5;

/**
 * Self-contained, prop-less: fetches `GET /api/interviews/upcoming?limit=5` itself and
 * renders its own loading/empty/error states, so it can be dropped into
 * StudentDashboardPage (or anywhere else) with no wiring. Never shows a hard-coded
 * number — an empty result renders an honest "No upcoming interviews" message, not a
 * zero tile (§69).
 */
export function UpcomingInterviewsCard() {
  const [interviews, setInterviews] = useState<InterviewResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    interviewService
      .listUpcoming(LIMIT)
      .then((data) => {
        if (!cancelled) setInterviews(data);
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, "Failed to load your upcoming interviews."));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <CalendarClockIcon className="size-4 text-muted-foreground" />
          Upcoming interviews
        </CardTitle>
        <CardDescription>Your next scheduled interviews.</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {!isLoading && !error && interviews?.length === 0 && (
          <p className="text-sm text-muted-foreground">No upcoming interviews.</p>
        )}
        {!isLoading && !error && interviews && interviews.length > 0 && (
          <ul className="space-y-2">
            {interviews.map((interview) => (
              <li
                key={interview.id}
                className="flex flex-col gap-1 border-b border-border py-2 text-sm last:border-0 sm:flex-row sm:items-center sm:justify-between"
              >
                <span className="min-w-0">
                  <span className="font-medium">{interview.title}</span>{" "}
                  <span className="text-muted-foreground">
                    ({INTERVIEW_TYPE_LABELS[interview.interviewType]}
                    {interview.companyName ? ` — ${interview.companyName}` : ""})
                  </span>
                </span>
                <span className="flex shrink-0 items-center gap-2 text-muted-foreground">
                  {formatDateTime(interview.scheduledStart)}
                  <InterviewStatusBadge status={interview.status} />
                </span>
              </li>
            ))}
          </ul>
        )}
        <p className="pt-2 text-sm text-muted-foreground">
          <Link to="/student/interviews" className="underline underline-offset-2 hover:text-foreground">
            View all interviews →
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}

export default UpcomingInterviewsCard;
