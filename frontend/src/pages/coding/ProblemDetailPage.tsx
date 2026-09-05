import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { CheckCircle2Icon, PlayIcon, SendIcon, XCircleIcon } from "lucide-react";

import { CodeEditor } from "@/components/coding/CodeEditor";
import { DifficultyBadge } from "@/components/coding/DifficultyBadge";
import { StatusBadge } from "@/components/coding/StatusBadge";
import { VerdictPanel } from "@/components/coding/VerdictPanel";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as codingService from "@/services/codingService";
import * as problemService from "@/services/problemService";
import { useAuth } from "@/hooks/useAuth";
import { extractErrorMessage } from "@/utils/apiError";
import type {
  LanguageResponse,
  ProblemDetailResponse,
  ProgrammingLanguage,
  SampleRunResponse,
  SubmissionDetailResponse,
  SubmissionSummaryResponse,
} from "@/types/coding";

export default function ProblemDetailPage() {
  const { problemId } = useParams<{ problemId: string }>();
  const id = Number(problemId);
  const [searchParams] = useSearchParams();
  const contestIdParam = searchParams.get("contestId");
  const contestId = contestIdParam ? Number(contestIdParam) : undefined;
  const { user } = useAuth();
  const canSubmit = user?.role === "STUDENT";
  const canSeeHistory = user?.role !== "FACULTY";

  const [problem, setProblem] = useState<ProblemDetailResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const [languages, setLanguages] = useState<LanguageResponse[]>([]);
  const [language, setLanguage] = useState<ProgrammingLanguage>("JAVA");
  const [sourceByLanguage, setSourceByLanguage] = useState<Partial<Record<ProgrammingLanguage, string>>>({});

  const [runResult, setRunResult] = useState<SampleRunResponse | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [isRunning, setIsRunning] = useState(false);

  const [submission, setSubmission] = useState<SubmissionDetailResponse | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [history, setHistory] = useState<SubmissionSummaryResponse[]>([]);
  const [historyError, setHistoryError] = useState<string | null>(null);

  const loadHistory = useCallback(() => {
    if (!canSeeHistory || !id) return;
    codingService
      .listSubmissions({ problemId: id, contestId, page: 0, size: 10, sort: "id,desc" })
      .then((page) => setHistory(page.content))
      .catch((err) => setHistoryError(extractErrorMessage(err, "Failed to load submission history.")));
  }, [canSeeHistory, id, contestId]);

  // Loading/error reset for the id-driven fetch below happens during render, not as
  // the first statements inside the effect — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes.
  const [fetchedForId, setFetchedForId] = useState<number | null>(null);
  if (id && id !== fetchedForId) {
    setFetchedForId(id);
    setIsLoading(true);
    setLoadError(null);
  }

  useEffect(() => {
    if (!id) return;
    Promise.all([problemService.getProblem(id), codingService.listLanguages()])
      .then(([problemData, languageList]) => {
        setProblem(problemData);
        setLanguages(languageList);
        const defaults: Partial<Record<ProgrammingLanguage, string>> = {};
        for (const lang of languageList) {
          defaults[lang.language] = lang.defaultTemplate;
        }
        setSourceByLanguage(defaults);
        if (languageList.length > 0) setLanguage(languageList[0].language);
      })
      .catch((err) => setLoadError(extractErrorMessage(err, "Failed to load this problem.")))
      .finally(() => setIsLoading(false));
  }, [id]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  const currentLanguageMeta = useMemo(
    () => languages.find((l) => l.language === language),
    [languages, language],
  );
  const sourceCode = sourceByLanguage[language] ?? "";

  function handleSourceChange(next: string) {
    setSourceByLanguage((prev) => ({ ...prev, [language]: next }));
  }

  async function handleRun() {
    if (!id) return;
    setIsRunning(true);
    setRunError(null);
    setRunResult(null);
    try {
      const result = await codingService.runSampleCases(id, { language, sourceCode });
      setRunResult(result);
    } catch (err) {
      setRunError(extractErrorMessage(err, "Code execution is unavailable right now."));
    } finally {
      setIsRunning(false);
    }
  }

  async function handleSubmit() {
    if (!id) return;
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const result = await codingService.submitSolution({
        problemId: id,
        language,
        sourceCode,
        contestId: contestId ?? null,
      });
      setSubmission(result);
      loadHistory();
    } catch (err) {
      setSubmitError(extractErrorMessage(err, "Failed to submit this solution."));
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return <p className="text-muted-foreground">Loading…</p>;
  }

  if (loadError || !problem) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{loadError ?? "This problem could not be found."}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <div className="space-y-4">
        {contestId != null && (
          <Alert>
            <AlertDescription>
              Submitting as part of{" "}
              <Link to={`/coding/contests/${contestId}`} className="font-medium hover:underline">
                contest #{contestId}
              </Link>
              . Submissions here count toward that contest's leaderboard.
            </AlertDescription>
          </Alert>
        )}
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{problem.title}</h1>
            <DifficultyBadge difficulty={problem.difficulty} />
            {!problem.published && <Badge variant="outline">Unpublished (admin preview)</Badge>}
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            Time limit {problem.timeLimitMs} ms · Memory limit {Math.round(problem.memoryLimitKb / 1024)} MB
          </p>
          <div className="mt-2 flex flex-wrap gap-1">
            {problem.tags.map((tag) => (
              <Badge key={tag} variant="secondary" className="text-xs">
                {tag}
              </Badge>
            ))}
          </div>
        </div>

        <Card>
          <CardContent className="space-y-4 pt-6 text-sm">
            <div>
              <h2 className="mb-1 font-medium">Description</h2>
              <p className="whitespace-pre-wrap text-muted-foreground">{problem.description}</p>
            </div>
            {problem.inputFormat && (
              <div>
                <h2 className="mb-1 font-medium">Input format</h2>
                <p className="whitespace-pre-wrap text-muted-foreground">{problem.inputFormat}</p>
              </div>
            )}
            {problem.outputFormat && (
              <div>
                <h2 className="mb-1 font-medium">Output format</h2>
                <p className="whitespace-pre-wrap text-muted-foreground">{problem.outputFormat}</p>
              </div>
            )}
            {problem.constraintsText && (
              <div>
                <h2 className="mb-1 font-medium">Constraints</h2>
                <p className="whitespace-pre-wrap text-muted-foreground">{problem.constraintsText}</p>
              </div>
            )}
            {(problem.sampleInput || problem.sampleOutput) && (
              <div className="grid gap-2 sm:grid-cols-2">
                {problem.sampleInput && (
                  <div>
                    <h2 className="mb-1 font-medium">Sample input</h2>
                    <pre className="overflow-auto rounded-lg bg-muted p-2 text-xs whitespace-pre-wrap">
                      {problem.sampleInput}
                    </pre>
                  </div>
                )}
                {problem.sampleOutput && (
                  <div>
                    <h2 className="mb-1 font-medium">Sample output</h2>
                    <pre className="overflow-auto rounded-lg bg-muted p-2 text-xs whitespace-pre-wrap">
                      {problem.sampleOutput}
                    </pre>
                  </div>
                )}
              </div>
            )}
            {problem.sampleTestCases.length > 0 && (
              <div>
                <h2 className="mb-1 font-medium">Sample test cases</h2>
                <div className="space-y-2">
                  {problem.sampleTestCases.map((sample) => (
                    <div key={sample.id} className="grid gap-2 rounded-lg border border-border p-2 sm:grid-cols-2">
                      <div>
                        <p className="text-xs text-muted-foreground">Input #{sample.ordinal}</p>
                        <pre className="mt-1 overflow-auto rounded bg-muted p-2 text-xs whitespace-pre-wrap">
                          {sample.input}
                        </pre>
                      </div>
                      <div>
                        <p className="text-xs text-muted-foreground">Expected output</p>
                        <pre className="mt-1 overflow-auto rounded bg-muted p-2 text-xs whitespace-pre-wrap">
                          {sample.expectedOutput}
                        </pre>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
            <p className="text-xs text-muted-foreground">
              {problem.hiddenTestCaseCount} hidden test case{problem.hiddenTestCaseCount === 1 ? "" : "s"} also run
              on submit.
            </p>
          </CardContent>
        </Card>

        {canSeeHistory && (
          <Card>
            <CardHeader>
              <CardTitle>Your submissions for this problem</CardTitle>
              <CardDescription>Most recent first.</CardDescription>
            </CardHeader>
            <CardContent className="px-0">
              {historyError && (
                <div className="px-4 pb-2">
                  <Alert variant="destructive">
                    <AlertDescription>{historyError}</AlertDescription>
                  </Alert>
                </div>
              )}
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Status</TableHead>
                    <TableHead>Language</TableHead>
                    <TableHead>Score</TableHead>
                    <TableHead>Submitted</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {history.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} className="py-6 text-center text-muted-foreground">
                        No submissions yet.
                      </TableCell>
                    </TableRow>
                  )}
                  {history.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <StatusBadge status={item.status} />
                      </TableCell>
                      <TableCell>{item.language}</TableCell>
                      <TableCell>
                        {item.score}/{item.maxScore}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {new Date(item.submittedAt).toLocaleString()}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>

      <div className="space-y-4">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <div>
              <CardTitle>Solution</CardTitle>
              <CardDescription>{currentLanguageMeta?.label ?? language}</CardDescription>
            </div>
            <Select value={language} onValueChange={(value) => value && setLanguage(value as ProgrammingLanguage)}>
              <SelectTrigger className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {languages.map((lang) => (
                  <SelectItem key={lang.language} value={lang.language}>
                    {lang.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </CardHeader>
          <CardContent className="space-y-3">
            <CodeEditor
              language={currentLanguageMeta?.monacoLanguageId ?? "plaintext"}
              value={sourceCode}
              onChange={handleSourceChange}
            />
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" onClick={handleRun} disabled={isRunning || !sourceCode.trim()}>
                <PlayIcon />
                {isRunning ? "Running…" : "Run sample tests"}
              </Button>
              {canSubmit ? (
                <Button type="button" onClick={handleSubmit} disabled={isSubmitting || !sourceCode.trim()}>
                  <SendIcon />
                  {isSubmitting ? "Submitting…" : "Submit"}
                </Button>
              ) : (
                <p className="self-center text-xs text-muted-foreground">Only students can submit solutions.</p>
              )}
            </div>

            {runError && (
              <Alert variant="destructive">
                <AlertTitle>Code execution is unavailable</AlertTitle>
                <AlertDescription>{runError}</AlertDescription>
              </Alert>
            )}

            {runResult && (
              <div className="space-y-2">
                <div className="flex items-center gap-2 text-sm">
                  {runResult.allPassed ? (
                    <CheckCircle2Icon className="size-4 text-emerald-600 dark:text-emerald-400" />
                  ) : (
                    <XCircleIcon className="size-4 text-rose-600 dark:text-rose-400" />
                  )}
                  {runResult.allPassed ? "All sample tests passed" : "Some sample tests did not pass"}
                </div>
                {runResult.cases.map((c) => (
                  <div key={c.ordinal} className="rounded-lg border border-border p-3 text-xs">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">Sample #{c.ordinal}</span>
                      <StatusBadge status={c.status} />
                      {c.passed ? (
                        <CheckCircle2Icon className="size-3.5 text-emerald-600 dark:text-emerald-400" />
                      ) : (
                        <XCircleIcon className="size-3.5 text-rose-600 dark:text-rose-400" />
                      )}
                    </div>
                    <div className="mt-2 grid gap-2 sm:grid-cols-2">
                      <div>
                        <p className="text-muted-foreground">Input</p>
                        <pre className="mt-1 max-h-28 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                          {c.input}
                        </pre>
                      </div>
                      <div>
                        <p className="text-muted-foreground">Expected</p>
                        <pre className="mt-1 max-h-28 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                          {c.expectedOutput}
                        </pre>
                      </div>
                      <div>
                        <p className="text-muted-foreground">Your output</p>
                        <pre className="mt-1 max-h-28 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                          {c.actualOutput ?? "(none)"}
                        </pre>
                      </div>
                      {c.stderr && (
                        <div>
                          <p className="text-muted-foreground">stderr</p>
                          <pre className="mt-1 max-h-28 overflow-auto rounded bg-muted p-2 whitespace-pre-wrap">
                            {c.stderr}
                          </pre>
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        {submitError && (
          <Alert variant="destructive">
            <AlertDescription>{submitError}</AlertDescription>
          </Alert>
        )}

        {submission && (
          <Card>
            <CardHeader>
              <CardTitle>Latest verdict</CardTitle>
              <CardDescription>
                <Link to={`/coding/submissions`} className="hover:underline">
                  View all your submissions
                </Link>
              </CardDescription>
            </CardHeader>
            <CardContent>
              <VerdictPanel submission={submission} />
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  );
}
