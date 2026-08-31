import { useEffect, useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import * as aiService from "@/services/aiService";
import type { AIStudentContextResponse } from "@/types/ai";
import { extractErrorMessage } from "@/utils/apiError";

/** Honest rendering of a nullable percentage/number — never 0, never invented. */
function formatNullable(value: number | string | null | undefined, suffix = ""): string {
  return value === null || value === undefined ? "—" : `${value}${suffix}`;
}

interface AcademicContextPanelProps {
  /** Fires once real data has loaded, so a caller (e.g. the study-plan generator's
   * subject picker) can reuse the same fetch instead of issuing a second one. */
  onLoaded?: (context: AIStudentContextResponse) => void;
}

/**
 * The real academic record — from `GET /api/ai/context` — that every AI answer in this
 * session is grounded in. Every figure here comes straight from the API; a value that
 * cannot be computed renders as "—", never 0 or a placeholder.
 */
export function AcademicContextPanel({ onLoaded }: AcademicContextPanelProps) {
  const [context, setContext] = useState<AIStudentContextResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    aiService
      .getContext()
      .then((data) => {
        if (cancelled) return;
        setContext(data);
        onLoaded?.(data);
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, "Failed to load your academic context."));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) {
    return (
      <Card>
        <CardContent className="py-6 text-center text-sm text-muted-foreground">
          Loading your academic context…
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  if (!context) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your academic record</CardTitle>
        <CardDescription>
          {context.hasAcademicData
            ? "The assistant grounds its answers in this record, not a general guess."
            : "No graded marks or attendance yet — the assistant will answer without grounding."}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <div>
            <p className="text-xs text-muted-foreground">CGPA</p>
            <p className="text-lg font-semibold tracking-tight">{formatNullable(context.cgpa)}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Graded credits</p>
            <p className="text-lg font-semibold tracking-tight">
              {formatNullable(context.totalGradedCredits)}
            </p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Attendance</p>
            <p className="text-lg font-semibold tracking-tight">
              {formatNullable(context.overallAttendancePercentage, "%")}
              {context.lowAttendance && (
                <Badge variant="destructive" className="ml-2 align-middle">
                  Below minimum
                </Badge>
              )}
            </p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Minimum required</p>
            <p className="text-lg font-semibold tracking-tight">
              {formatNullable(context.minimumAttendancePercentage, "%")}
            </p>
          </div>
        </div>

        <div>
          <p className="mb-1 text-xs font-medium text-muted-foreground">Weak subjects</p>
          {context.weakSubjects.length === 0 ? (
            <p className="text-sm text-muted-foreground">None identified.</p>
          ) : (
            <ul className="space-y-1">
              {context.weakSubjects.map((weak, index) => (
                <li key={weak.subjectId ?? index} className="text-sm">
                  <span className="font-medium">{weak.subjectCode ?? weak.subjectName ?? "Subject"}</span>
                  {weak.subjectName && weak.subjectCode ? ` — ${weak.subjectName}` : ""}
                  <span className="text-muted-foreground">
                    {" "}
                    (marks {formatNullable(weak.marksPercentage, "%")}, attendance{" "}
                    {formatNullable(weak.attendancePercentage, "%")}) — {weak.reason}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div>
          <p className="mb-1 text-xs font-medium text-muted-foreground">Upcoming exams</p>
          {context.upcomingExams.length === 0 ? (
            <p className="text-sm text-muted-foreground">None scheduled.</p>
          ) : (
            <ul className="space-y-1">
              {context.upcomingExams.map((exam) => (
                <li key={exam.id} className="text-sm">
                  <span className="font-medium">{exam.title}</span>{" "}
                  <span className="text-muted-foreground">
                    ({exam.subjectCode} — {exam.subjectName}) on {exam.examDate}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
