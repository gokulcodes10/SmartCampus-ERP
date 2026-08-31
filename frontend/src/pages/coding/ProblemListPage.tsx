import { useMemo, useState } from "react";
import { Link } from "react-router-dom";

import { DifficultyBadge } from "@/components/coding/DifficultyBadge";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as problemService from "@/services/problemService";
import type { ProblemDifficulty } from "@/types/coding";

const ALL_DIFFICULTIES = "all";
const DIFFICULTIES: ProblemDifficulty[] = ["EASY", "MEDIUM", "HARD"];

/** Practice-problem catalog (`/coding`), open to every authenticated role. */
export default function ProblemListPage() {
  const [difficulty, setDifficulty] = useState<string>(ALL_DIFFICULTIES);

  const filters = useMemo(
    () => (difficulty === ALL_DIFFICULTIES ? {} : { difficulty: difficulty as ProblemDifficulty }),
    [difficulty],
  );

  const { data, isLoading, error, setPage, search, setSearch } = useServerTable(
    problemService.listProblems,
    filters,
    { sort: "id,desc" },
  );

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Problems</h1>
        <p className="text-muted-foreground">Practice coding problems, from easy warm-ups to hard challenges.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Catalog</CardTitle>
          <CardDescription>Search by title or slug, or filter by difficulty.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search problems…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={difficulty} onValueChange={(value) => setDifficulty(value ?? ALL_DIFFICULTIES)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All difficulties" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_DIFFICULTIES}>All difficulties</SelectItem>
                {DIFFICULTIES.map((d) => (
                  <SelectItem key={d} value={d}>
                    {d.charAt(0) + d.slice(1).toLowerCase()}
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
                <TableHead>Difficulty</TableHead>
                <TableHead>Tags</TableHead>
                <TableHead>Time / Memory limit</TableHead>
                <TableHead className="text-right">Test cases</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                    No problems found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((problem) => (
                  <TableRow key={problem.id}>
                    <TableCell className="font-medium">
                      <Link to={`/coding/problems/${problem.id}`} className="hover:underline">
                        {problem.title}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <DifficultyBadge difficulty={problem.difficulty} />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {problem.tags.length === 0 && <span className="text-xs text-muted-foreground">—</span>}
                        {problem.tags.map((tag) => (
                          <Badge key={tag} variant="secondary" className="text-xs">
                            {tag}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {problem.timeLimitMs} ms / {Math.round(problem.memoryLimitKb / 1024)} MB
                    </TableCell>
                    <TableCell className="text-right text-sm text-muted-foreground">
                      {problem.sampleTestCaseCount} sample + {problem.hiddenTestCaseCount} hidden
                    </TableCell>
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
