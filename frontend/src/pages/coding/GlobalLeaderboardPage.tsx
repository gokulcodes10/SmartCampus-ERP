import { useEffect, useState } from "react";
import { TrophyIcon } from "lucide-react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as leaderboardService from "@/services/leaderboardService";
import { extractErrorMessage } from "@/utils/apiError";
import type { GlobalLeaderboardRowResponse } from "@/types/coding";

const PAGE_SIZE = 20;

/** `/coding/leaderboard` — practice-wide standings, open to every authenticated role. */
export default function GlobalLeaderboardPage() {
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<GlobalLeaderboardRowResponse[]>([]);
  const [total, setTotal] = useState({ totalElements: 0, totalPages: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Reset loading/error during render when `page` changes, rather than as the first
  // statements inside the effect — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes.
  const [fetchedPage, setFetchedPage] = useState<number | null>(null);
  if (page !== fetchedPage) {
    setFetchedPage(page);
    setIsLoading(true);
    setError(null);
  }

  useEffect(() => {
    let cancelled = false;
    leaderboardService
      .getGlobalLeaderboard({ page, size: PAGE_SIZE })
      .then((result) => {
        if (cancelled) return;
        setRows(result.content);
        setTotal({ totalElements: result.totalElements, totalPages: result.totalPages });
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, "Failed to load the leaderboard."));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
          <TrophyIcon className="size-6" />
          Global leaderboard
        </h1>
        <p className="text-muted-foreground">
          Ranked by distinct problems solved across practice and contests, weighted by difficulty.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Standings</CardTitle>
          <CardDescription>
            Score counts each solved problem once — easy, medium and hard problems are worth different points.
          </CardDescription>
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
                <TableHead className="w-16">Rank</TableHead>
                <TableHead>Student</TableHead>
                <TableHead>Department</TableHead>
                <TableHead>Problems solved</TableHead>
                <TableHead>Score</TableHead>
                <TableHead>Last accepted</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    No one has solved a problem yet.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                rows.map((row) => (
                  <TableRow key={row.studentId}>
                    <TableCell className="font-medium">{row.rank}</TableCell>
                    <TableCell>
                      {row.studentName} {row.registerNumber ? `(${row.registerNumber})` : ""}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{row.departmentName ?? "—"}</TableCell>
                    <TableCell>{row.problemsSolved}</TableCell>
                    <TableCell>{row.totalScore}</TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {row.lastAcceptedAt ? new Date(row.lastAcceptedAt).toLocaleString() : "—"}
                    </TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>

          <PaginationBar
            page={page}
            size={PAGE_SIZE}
            totalElements={total.totalElements}
            totalPages={total.totalPages}
            onPageChange={setPage}
          />
        </CardContent>
      </Card>
    </div>
  );
}
