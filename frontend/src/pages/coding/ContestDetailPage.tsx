import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { CheckCircle2Icon, LockIcon, TimerIcon, TrophyIcon } from "lucide-react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { DifficultyBadge } from "@/components/coding/DifficultyBadge";
import { StatusBadge } from "@/components/coding/StatusBadge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuth } from "@/hooks/useAuth";
import * as contestService from "@/services/contestService";
import { extractErrorMessage } from "@/utils/apiError";
import type {
  ContestDetailResponse,
  ContestLeaderboardRowResponse,
  ContestParticipantResponse,
} from "@/types/coding";

function formatCountdown(targetIso: string, now: Date): string {
  const diffMs = new Date(targetIso).getTime() - now.getTime();
  if (diffMs <= 0) return "00:00:00";
  const totalSeconds = Math.floor(diffMs / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

const LEADERBOARD_PAGE_SIZE = 20;

export default function ContestDetailPage() {
  const { contestId } = useParams<{ contestId: string }>();
  const id = Number(contestId);
  const { user } = useAuth();
  const isStudent = user?.role === "STUDENT";

  const [contest, setContest] = useState<ContestDetailResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const [participation, setParticipation] = useState<ContestParticipantResponse | null>(null);

  const [isRegistering, setIsRegistering] = useState(false);
  const [registerError, setRegisterError] = useState<string | null>(null);

  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const [leaderboard, setLeaderboard] = useState<ContestLeaderboardRowResponse[]>([]);
  const [leaderboardPage, setLeaderboardPage] = useState(0);
  const [leaderboardTotal, setLeaderboardTotal] = useState({ totalElements: 0, totalPages: 0 });
  const [leaderboardError, setLeaderboardError] = useState<string | null>(null);
  const [isLeaderboardLoading, setIsLeaderboardLoading] = useState(true);

  // The loading/error resets that used to run synchronously at the top of these two
  // effects are adjusted during render instead — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes.
  // Each effect below is left doing only the async fetch, whose own setState calls are
  // already deferred into .then/.catch/.finally.
  const [fetchedForId, setFetchedForId] = useState<number | null>(null);
  if (id && id !== fetchedForId) {
    setFetchedForId(id);
    setIsLoading(true);
    setLoadError(null);
  }

  function fetchContest() {
    if (!id) return;
    contestService
      .getContest(id)
      .then((data) => {
        setContest(data);
        if (data.registered) {
          contestService
            .getMyParticipation(id)
            .then(setParticipation)
            .catch(() => setParticipation(null));
        } else {
          setParticipation(null);
        }
      })
      .catch((err) => setLoadError(extractErrorMessage(err, "Failed to load this contest.")))
      .finally(() => setIsLoading(false));
  }

  useEffect(fetchContest, [id]);

  const leaderboardKey = `${id}:${leaderboardPage}`;
  const [fetchedLeaderboardKey, setFetchedLeaderboardKey] = useState<string | null>(null);
  if (id && leaderboardKey !== fetchedLeaderboardKey) {
    setFetchedLeaderboardKey(leaderboardKey);
    setIsLeaderboardLoading(true);
    setLeaderboardError(null);
  }

  useEffect(() => {
    if (!id) return;
    contestService
      .getContestLeaderboard(id, { page: leaderboardPage, size: LEADERBOARD_PAGE_SIZE })
      .then((page) => {
        setLeaderboard(page.content);
        setLeaderboardTotal({ totalElements: page.totalElements, totalPages: page.totalPages });
      })
      .catch((err) => setLeaderboardError(extractErrorMessage(err, "Failed to load the leaderboard.")))
      .finally(() => setIsLeaderboardLoading(false));
  }, [id, leaderboardPage]);

  const countdownLabel = useMemo(() => {
    if (!contest) return null;
    if (contest.phase === "UPCOMING") return `Starts in ${formatCountdown(contest.startTime, now)}`;
    if (contest.phase === "RUNNING") return `Ends in ${formatCountdown(contest.endTime, now)}`;
    return "Contest has ended";
  }, [contest, now]);

  async function handleRegister() {
    if (!id) return;
    setIsRegistering(true);
    setRegisterError(null);
    try {
      await contestService.registerForContest(id);
      setIsLoading(true);
      setLoadError(null);
      fetchContest();
    } catch (err) {
      setRegisterError(extractErrorMessage(err, "Failed to register for this contest."));
    } finally {
      setIsRegistering(false);
    }
  }

  if (isLoading) {
    return <p className="text-muted-foreground">Loading…</p>;
  }

  if (loadError || !contest) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{loadError ?? "This contest could not be found."}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-semibold tracking-tight">{contest.title}</h1>
          {contest.status !== "PUBLISHED" && <Badge variant="outline">{contest.status}</Badge>}
        </div>
        {contest.description && <p className="mt-1 text-muted-foreground">{contest.description}</p>}
        <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
          <span className="flex items-center gap-1 font-medium">
            <TimerIcon className="size-4" />
            {countdownLabel}
          </span>
          <span className="text-muted-foreground">
            {new Date(contest.startTime).toLocaleString()} – {new Date(contest.endTime).toLocaleString()}
          </span>
          <span className="text-muted-foreground">
            Penalty: {contest.penaltyMinutesPerWrongAttempt} min / wrong attempt
          </span>
        </div>
      </div>

      {isStudent && (
        <Card>
          <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
            {contest.registered ? (
              <span className="flex items-center gap-2 text-sm text-emerald-700 dark:text-emerald-400">
                <CheckCircle2Icon className="size-4" />
                You are registered for this contest.
              </span>
            ) : (
              <div>
                <p className="text-sm text-muted-foreground">You are not registered for this contest yet.</p>
                {registerError && <p className="mt-1 text-xs text-destructive">{registerError}</p>}
              </div>
            )}
            {!contest.registered && (
              <Button type="button" onClick={handleRegister} disabled={isRegistering}>
                {isRegistering ? "Registering…" : "Register"}
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      {participation && (
        <Card>
          <CardHeader>
            <CardTitle>Your standing</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-6 text-sm">
            <span>
              Score: <span className="font-medium">{participation.totalScore}</span>
            </span>
            <span>
              Problems solved: <span className="font-medium">{participation.problemsSolved}</span>
            </span>
            <span>
              Penalty: <span className="font-medium">{Math.round(participation.penaltySeconds / 60)} min</span>
            </span>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Problems</CardTitle>
          <CardDescription>
            {contest.problemsVisible
              ? `${contest.problems.length} problem${contest.problems.length === 1 ? "" : "s"}`
              : "Hidden until the contest begins"}
          </CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          {!contest.problemsVisible ? (
            <div className="flex items-center gap-2 px-4 py-8 text-sm text-muted-foreground">
              <LockIcon className="size-4" />
              Problems are revealed when the contest begins.
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-14">#</TableHead>
                  <TableHead>Title</TableHead>
                  <TableHead>Difficulty</TableHead>
                  <TableHead>Points</TableHead>
                  {isStudent && <TableHead>Your status</TableHead>}
                  {isStudent && <TableHead className="text-right">Attempts</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {contest.problems.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={isStudent ? 6 : 4} className="py-8 text-center text-muted-foreground">
                      No problems have been added to this contest yet.
                    </TableCell>
                  </TableRow>
                )}
                {contest.problems.map((problem) => (
                  <TableRow key={problem.id}>
                    <TableCell className="font-mono">{problem.label}</TableCell>
                    <TableCell className="font-medium">
                      <Link
                        to={`/coding/problems/${problem.problemId}?contestId=${contest.id}`}
                        className="hover:underline"
                      >
                        {problem.problemTitle}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <DifficultyBadge difficulty={problem.difficulty} />
                    </TableCell>
                    <TableCell>{problem.points}</TableCell>
                    {isStudent && (
                      <TableCell>
                        {problem.myBestStatus ? <StatusBadge status={problem.myBestStatus} /> : "—"}
                      </TableCell>
                    )}
                    {isStudent && <TableCell className="text-right">{problem.myAttempts}</TableCell>}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <TrophyIcon className="size-4" />
            Leaderboard
          </CardTitle>
          <CardDescription>Ranked by score, then penalty time, then earliest last accept.</CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          {leaderboardError && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{leaderboardError}</AlertDescription>
              </Alert>
            </div>
          )}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-16">Rank</TableHead>
                <TableHead>Student</TableHead>
                <TableHead>Department</TableHead>
                <TableHead>Score</TableHead>
                <TableHead>Solved</TableHead>
                <TableHead>Penalty</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLeaderboardLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLeaderboardLoading && leaderboard.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    No one has scored yet.
                  </TableCell>
                </TableRow>
              )}
              {!isLeaderboardLoading &&
                leaderboard.map((row) => (
                  <TableRow key={row.studentId}>
                    <TableCell className="font-medium">{row.rank}</TableCell>
                    <TableCell>
                      {row.studentName} {row.registerNumber ? `(${row.registerNumber})` : ""}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{row.departmentName ?? "—"}</TableCell>
                    <TableCell>{row.totalScore}</TableCell>
                    <TableCell>{row.problemsSolved}</TableCell>
                    <TableCell>{Math.round(row.penaltySeconds / 60)} min</TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>

          <PaginationBar
            page={leaderboardPage}
            size={LEADERBOARD_PAGE_SIZE}
            totalElements={leaderboardTotal.totalElements}
            totalPages={leaderboardTotal.totalPages}
            onPageChange={setLeaderboardPage}
          />
        </CardContent>
      </Card>
    </div>
  );
}
