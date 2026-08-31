import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { ChevronDownIcon, ChevronRightIcon } from "lucide-react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { StatusBadge } from "@/components/coding/StatusBadge";
import { VerdictPanel } from "@/components/coding/VerdictPanel";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import { useAuth } from "@/hooks/useAuth";
import * as codingService from "@/services/codingService";
import { extractErrorMessage } from "@/utils/apiError";
import type { Page, SubmissionDetailResponse, SubmissionStatus, SubmissionSummaryResponse } from "@/types/coding";

const ALL_STATUSES = "all";
const STATUSES: SubmissionStatus[] = [
  "PENDING",
  "RUNNING",
  "ACCEPTED",
  "WRONG_ANSWER",
  "TIME_LIMIT_EXCEEDED",
  "MEMORY_LIMIT_EXCEEDED",
  "COMPILATION_ERROR",
  "RUNTIME_ERROR",
  "INTERNAL_ERROR",
];

/**
 * `/coding/submissions`. STUDENT sees only their own submissions; ADMIN can see any
 * (and additionally filter by student id, since that param is honoured for ADMIN
 * only). FACULTY has no read surface here at all (R7/R8) — the page says so rather
 * than firing a request that is guaranteed a 403.
 */
export default function SubmissionsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";
  // FACULTY has no read surface here at all (R7/R8): every hook below still runs
  // (React's rules of hooks require that), but the fetcher short-circuits without
  // ever calling an endpoint that is guaranteed a 403, and the component renders only
  // an explanatory message for that role at the very end.
  const isFaculty = user?.role === "FACULTY";

  const [status, setStatus] = useState<string>(ALL_STATUSES);
  const [problemIdInput, setProblemIdInput] = useState("");
  const [studentIdInput, setStudentIdInput] = useState("");

  const filters = useMemo(() => {
    const f: { status?: SubmissionStatus; problemId?: number; studentId?: number } = {};
    if (status !== ALL_STATUSES) f.status = status as SubmissionStatus;
    const problemId = Number(problemIdInput);
    if (problemIdInput && Number.isInteger(problemId) && problemId > 0) f.problemId = problemId;
    if (isAdmin) {
      const studentId = Number(studentIdInput);
      if (studentIdInput && Number.isInteger(studentId) && studentId > 0) f.studentId = studentId;
    }
    return f;
  }, [status, problemIdInput, studentIdInput, isAdmin]);

  const fetchSubmissions = useCallback(
    (params: Parameters<typeof codingService.listSubmissions>[0]): Promise<Page<SubmissionSummaryResponse>> =>
      isFaculty
        ? Promise.resolve<Page<SubmissionSummaryResponse>>({
            content: [],
            page: 0,
            size: params?.size ?? 10,
            totalElements: 0,
            totalPages: 0,
          })
        : codingService.listSubmissions(params),
    [isFaculty],
  );

  const { data, isLoading, error, setPage } = useServerTable(fetchSubmissions, filters, {
    sort: "id,desc",
  });

  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [detailById, setDetailById] = useState<Record<number, SubmissionDetailResponse>>({});
  const [detailError, setDetailError] = useState<string | null>(null);
  const [loadingDetailId, setLoadingDetailId] = useState<number | null>(null);

  useEffect(() => {
    if (expandedId == null || detailById[expandedId]) return;
    setLoadingDetailId(expandedId);
    setDetailError(null);
    codingService
      .getSubmission(expandedId)
      .then((detail) => setDetailById((prev) => ({ ...prev, [expandedId]: detail })))
      .catch((err) => setDetailError(extractErrorMessage(err, "Failed to load this submission.")))
      .finally(() => setLoadingDetailId(null));
  }, [expandedId, detailById]);

  function toggleRow(id: number) {
    setExpandedId((current) => (current === id ? null : id));
  }

  if (isFaculty) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          Coding submissions are not visible to faculty. Coding is institution-wide, not scoped to a
          subject you teach, so there is no tuple this could be restricted to.
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Submissions</h1>
        <p className="text-muted-foreground">
          {isAdmin ? "Every submission across all students." : "Your coding submission history."}
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Narrow the list by status, problem, or (as admin) student.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Select value={status} onValueChange={(value) => setStatus(value ?? ALL_STATUSES)}>
              <SelectTrigger className="w-56">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
                {STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s.replaceAll("_", " ")}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input
              placeholder="Problem ID"
              inputMode="numeric"
              value={problemIdInput}
              onChange={(event) => setProblemIdInput(event.target.value)}
              className="w-32"
            />
            {isAdmin && (
              <Input
                placeholder="Student ID"
                inputMode="numeric"
                value={studentIdInput}
                onChange={(event) => setStudentIdInput(event.target.value)}
                className="w-32"
              />
            )}
          </div>
        </CardHeader>
        <CardContent className="px-0">
          {error && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-8" />
                <TableHead>Problem</TableHead>
                {isAdmin && <TableHead>Student</TableHead>}
                <TableHead>Language</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Score</TableHead>
                <TableHead>Submitted</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={isAdmin ? 7 : 6} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={isAdmin ? 7 : 6} className="py-8 text-center text-muted-foreground">
                    No submissions found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((item) => (
                  <Fragment key={item.id}>
                    <TableRow className="cursor-pointer" onClick={() => toggleRow(item.id)}>
                      <TableCell>
                        <Button variant="ghost" size="icon-sm" type="button">
                          {expandedId === item.id ? <ChevronDownIcon /> : <ChevronRightIcon />}
                        </Button>
                      </TableCell>
                      <TableCell className="font-medium">{item.problemTitle}</TableCell>
                      {isAdmin && (
                        <TableCell className="text-sm text-muted-foreground">
                          {item.studentName} {item.registerNumber ? `(${item.registerNumber})` : ""}
                        </TableCell>
                      )}
                      <TableCell>{item.language}</TableCell>
                      <TableCell>
                        <StatusBadge status={item.status} />
                      </TableCell>
                      <TableCell>
                        {item.score}/{item.maxScore}
                      </TableCell>
                      <TableCell className="text-xs text-muted-foreground">
                        {new Date(item.submittedAt).toLocaleString()}
                      </TableCell>
                    </TableRow>
                    {expandedId === item.id && (
                      <TableRow>
                        <TableCell colSpan={isAdmin ? 7 : 6} className="bg-muted/30">
                          {loadingDetailId === item.id && (
                            <p className="py-4 text-center text-sm text-muted-foreground">Loading…</p>
                          )}
                          {detailError && loadingDetailId !== item.id && (
                            <Alert variant="destructive" className="my-2">
                              <AlertDescription>{detailError}</AlertDescription>
                            </Alert>
                          )}
                          {detailById[item.id] && (
                            <div className="py-3">
                              <VerdictPanel submission={detailById[item.id]} />
                            </div>
                          )}
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                ))}
            </TableBody>
          </Table>

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
