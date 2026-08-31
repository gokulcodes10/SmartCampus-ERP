import { AlertTriangleIcon, ClockIcon, CpuIcon } from "lucide-react";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { StatusBadge } from "@/components/coding/StatusBadge";
import type { SubmissionDetailResponse } from "@/types/coding";

/**
 * Renders one judged (or judge-failed) submission honestly.
 *
 * INTERNAL_ERROR means Judge0 could not be reached or did not return a verdict — it is
 * NOT a normal judging outcome. Judge0 has no reachable endpoint on this build machine
 * (G10), so this is the state every submission is expected to show right now. It is
 * rendered as a distinct amber "could not be judged" alert, never as a spinner that
 * never resolves and never as a green Accepted.
 */
export function VerdictPanel({ submission }: { submission: SubmissionDetailResponse }) {
  const {
    status,
    passedTestCases,
    totalTestCases,
    score,
    maxScore,
    executionTimeMs,
    memoryKb,
    failedTestCaseOrdinal,
    compileOutput,
    errorMessage,
    testResults,
  } = submission;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <StatusBadge status={status} />
        <span className="text-sm text-muted-foreground">
          {passedTestCases}/{totalTestCases} test cases passed
        </span>
        <span className="text-sm text-muted-foreground">
          Score: {score}/{maxScore}
        </span>
        {executionTimeMs != null && (
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <ClockIcon className="size-3.5" />
            {executionTimeMs} ms
          </span>
        )}
        {memoryKb != null && (
          <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <CpuIcon className="size-3.5" />
            {memoryKb} KB
          </span>
        )}
        {failedTestCaseOrdinal != null && (
          <span className="text-xs text-muted-foreground">Failed on test case #{failedTestCaseOrdinal}</span>
        )}
      </div>

      {status === "INTERNAL_ERROR" && (
        <Alert variant="destructive">
          <AlertTriangleIcon />
          <AlertTitle>Could not be judged</AlertTitle>
          <AlertDescription>
            {errorMessage ??
              "The code execution service did not return a verdict. This attempt is recorded, but it was never actually run — it is not a Wrong Answer."}
          </AlertDescription>
        </Alert>
      )}

      {status === "COMPILATION_ERROR" && compileOutput && (
        <div className="space-y-1">
          <p className="text-xs font-medium text-muted-foreground">Compiler output</p>
          <pre className="max-h-64 overflow-auto rounded-lg border border-border bg-muted p-3 text-xs whitespace-pre-wrap">
            {compileOutput}
          </pre>
        </div>
      )}

      {testResults.length > 0 && (
        <div className="space-y-2">
          <p className="text-xs font-medium text-muted-foreground">Test cases</p>
          <div className="space-y-2">
            {testResults.map((result) => (
              <div key={result.ordinal} className="rounded-lg border border-border p-3 text-xs">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">
                    #{result.ordinal} {result.isSample ? "(sample)" : "(hidden)"}
                  </span>
                  <StatusBadge status={result.status} />
                  {result.executionTimeMs != null && (
                    <span className="text-muted-foreground">{result.executionTimeMs} ms</span>
                  )}
                  {result.memoryKb != null && (
                    <span className="text-muted-foreground">{result.memoryKb} KB</span>
                  )}
                </div>
                {result.isSample ? (
                  <div className="mt-2 grid gap-2 sm:grid-cols-2">
                    <div>
                      <p className="text-muted-foreground">Input</p>
                      <pre className="mt-1 max-h-32 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                        {result.input ?? ""}
                      </pre>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Expected</p>
                      <pre className="mt-1 max-h-32 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                        {result.expectedOutput ?? ""}
                      </pre>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Your output</p>
                      <pre className="mt-1 max-h-32 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                        {result.actualOutput ?? "(none)"}
                      </pre>
                    </div>
                    {result.stderrOutput && (
                      <div>
                        <p className="text-muted-foreground">stderr</p>
                        <pre className="mt-1 max-h-32 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                          {result.stderrOutput}
                        </pre>
                      </div>
                    )}
                  </div>
                ) : (
                  <p className="mt-2 text-muted-foreground">
                    Hidden test case — inputs and expected output are not shown.
                  </p>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default VerdictPanel;
