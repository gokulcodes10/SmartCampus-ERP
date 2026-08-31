import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2Icon } from "lucide-react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import { useAuth } from "@/hooks/useAuth";
import * as contestService from "@/services/contestService";
import { extractErrorMessage } from "@/utils/apiError";
import type { ContestPhase, ContestSummaryResponse } from "@/types/coding";

const ALL_PHASES = "all";
const PHASES: ContestPhase[] = ["UPCOMING", "RUNNING", "ENDED"];

const PHASE_LABEL: Record<ContestPhase, string> = {
  UPCOMING: "Upcoming",
  RUNNING: "Running",
  ENDED: "Ended",
};

/** `/coding/contests` — open to every authenticated role; registration is STUDENT-only. */
export default function ContestListPage() {
  const { user } = useAuth();
  const isStudent = user?.role === "STUDENT";

  const [phase, setPhase] = useState<string>(ALL_PHASES);
  const [registeringId, setRegisteringId] = useState<number | null>(null);
  const [registerError, setRegisterError] = useState<{ id: number; message: string } | null>(null);

  const filters = useMemo(
    () => (phase === ALL_PHASES ? {} : { phase: phase as ContestPhase }),
    [phase],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    contestService.listContests,
    filters,
    { sort: "startTime,desc" },
  );

  async function handleRegister(contest: ContestSummaryResponse) {
    setRegisteringId(contest.id);
    setRegisterError(null);
    try {
      await contestService.registerForContest(contest.id);
      refresh();
    } catch (err) {
      setRegisterError({ id: contest.id, message: extractErrorMessage(err, "Failed to register.") });
    } finally {
      setRegisteringId(null);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Contests</h1>
        <p className="text-muted-foreground">Timed coding contests, ICPC-style scoring.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All contests</CardTitle>
          <CardDescription>Search by title, or filter by phase.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search contests…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={phase} onValueChange={(value) => setPhase(value ?? ALL_PHASES)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All phases" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_PHASES}>All phases</SelectItem>
                {PHASES.map((p) => (
                  <SelectItem key={p} value={p}>
                    {PHASE_LABEL[p]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
                <TableHead>Title</TableHead>
                <TableHead>Phase</TableHead>
                <TableHead>Starts</TableHead>
                <TableHead>Ends</TableHead>
                <TableHead>Problems</TableHead>
                <TableHead>Participants</TableHead>
                {isStudent && <TableHead className="text-right">Registration</TableHead>}
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={isStudent ? 7 : 6} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={isStudent ? 7 : 6} className="py-8 text-center text-muted-foreground">
                    No contests found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((contest) => (
                  <TableRow key={contest.id}>
                    <TableCell className="font-medium">
                      <Link to={`/coding/contests/${contest.id}`} className="hover:underline">
                        {contest.title}
                      </Link>
                      {contest.status !== "PUBLISHED" && (
                        <Badge variant="outline" className="ml-2 text-xs">
                          {contest.status}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{PHASE_LABEL[contest.phase]}</Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(contest.startTime).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(contest.endTime).toLocaleString()}
                    </TableCell>
                    <TableCell>{contest.problemCount}</TableCell>
                    <TableCell>{contest.participantCount}</TableCell>
                    {isStudent && (
                      <TableCell className="text-right">
                        {contest.registered ? (
                          <span className="inline-flex items-center gap-1 text-xs text-emerald-700 dark:text-emerald-400">
                            <CheckCircle2Icon className="size-3.5" />
                            Registered
                          </span>
                        ) : (
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            disabled={registeringId === contest.id}
                            onClick={() => handleRegister(contest)}
                          >
                            {registeringId === contest.id ? "Registering…" : "Register"}
                          </Button>
                        )}
                        {registerError?.id === contest.id && (
                          <p className="mt-1 text-xs text-destructive">{registerError.message}</p>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
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
