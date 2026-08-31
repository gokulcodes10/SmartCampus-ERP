import { useEffect, useState } from "react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as marksService from "@/services/marksService";
import type { Page } from "@/types/academic";
import type { AcademicResultResponse, MarksResponse } from "@/types/academicOps";
import { extractErrorMessage } from "@/utils/apiError";

const RECORDS_PAGE_SIZE = 20;

/** Renders a nullable numeric result honestly — a subject with no computable grade is
 * "Not graded", never 0 or an invented letter. */
function formatNullable(value: number | string | null, suffix = ""): string {
  return value === null || value === undefined ? "—" : `${value}${suffix}`;
}

export default function StudentMarksPage() {
  const [result, setResult] = useState<AcademicResultResponse | null>(null);
  const [resultLoading, setResultLoading] = useState(true);
  const [resultError, setResultError] = useState<string | null>(null);

  const [records, setRecords] = useState<Page<MarksResponse> | null>(null);
  const [recordsLoading, setRecordsLoading] = useState(true);
  const [recordsError, setRecordsError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setResultLoading(true);
    setResultError(null);
    marksService
      .getMySummary({})
      .then((data) => {
        if (!cancelled) setResult(data);
      })
      .catch((err) => {
        if (!cancelled) setResultError(extractErrorMessage(err, "Failed to load your academic result."));
      })
      .finally(() => {
        if (!cancelled) setResultLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    setRecordsLoading(true);
    setRecordsError(null);
    marksService
      .listMine({ page, size: RECORDS_PAGE_SIZE })
      .then((data) => {
        if (!cancelled) setRecords(data);
      })
      .catch((err) => {
        if (!cancelled) setRecordsError(extractErrorMessage(err, "Failed to load your marks records."));
      })
      .finally(() => {
        if (!cancelled) setRecordsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My marks</h1>
        <p className="text-muted-foreground">Results and grades computed from the current grade bands.</p>
      </div>

      {resultError && (
        <Alert variant="destructive">
          <AlertDescription>{resultError}</AlertDescription>
        </Alert>
      )}

      {resultLoading && (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">Loading your result…</CardContent>
        </Card>
      )}

      {!resultLoading && result && (
        <>
          <Card>
            <CardHeader>
              <CardTitle>CGPA</CardTitle>
              <CardDescription>
                Credit-weighted across every graded subject ({result.totalGradedCredits} graded credits).
              </CardDescription>
            </CardHeader>
            <CardContent>
              <span className="text-3xl font-semibold tracking-tight">{formatNullable(result.cgpa)}</span>
            </CardContent>
          </Card>

          {result.semesters.length === 0 && (
            <Card>
              <CardContent className="py-8 text-center text-muted-foreground">
                No marks recorded yet.
              </CardContent>
            </Card>
          )}

          {result.semesters.map((sem) => (
            <Card key={`${sem.academicYear}-${sem.semester}`}>
              <CardHeader>
                <CardTitle>
                  {sem.academicYear} — Semester {sem.semester}
                </CardTitle>
                <CardDescription>
                  GPA: <span className="font-medium text-foreground">{formatNullable(sem.gpa)}</span> ·{" "}
                  {sem.gradedCredits} graded credits across {sem.subjectCount} subject
                  {sem.subjectCount === 1 ? "" : "s"}
                </CardDescription>
              </CardHeader>
              <CardContent className="px-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Subject</TableHead>
                      <TableHead>Credits</TableHead>
                      <TableHead>Obtained / Max</TableHead>
                      <TableHead>Percentage</TableHead>
                      <TableHead>Grade</TableHead>
                      <TableHead>Grade point</TableHead>
                      <TableHead>Passed</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {sem.subjects.map((subject) => (
                      <TableRow key={subject.subjectId}>
                        <TableCell className="font-medium">
                          {subject.subjectCode} — {subject.subjectName}
                        </TableCell>
                        <TableCell>{subject.credits}</TableCell>
                        <TableCell>
                          {subject.totalObtained} / {subject.totalMaximum}
                        </TableCell>
                        <TableCell>{formatNullable(subject.percentage, "%")}</TableCell>
                        <TableCell>{formatNullable(subject.grade)}</TableCell>
                        <TableCell>{formatNullable(subject.gradePoint)}</TableCell>
                        <TableCell>
                          {subject.passed === null || subject.passed === undefined ? (
                            <Badge variant="secondary">Not graded</Badge>
                          ) : subject.passed ? (
                            <Badge variant="default">Pass</Badge>
                          ) : (
                            <Badge variant="destructive">Fail</Badge>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          ))}
        </>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Records</CardTitle>
          <CardDescription>Every individual exam result.</CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          {recordsError && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{recordsError}</AlertDescription>
              </Alert>
            </div>
          )}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Exam</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Subject</TableHead>
                <TableHead>Marks</TableHead>
                <TableHead>Grade</TableHead>
                <TableHead>Remarks</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {recordsLoading && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!recordsLoading && records?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    No records found.
                  </TableCell>
                </TableRow>
              )}
              {!recordsLoading &&
                records?.content.map((record) => (
                  <TableRow key={record.id}>
                    <TableCell>
                      {record.examTitle} <span className="text-muted-foreground">({record.examType})</span>
                    </TableCell>
                    <TableCell>{record.examDate}</TableCell>
                    <TableCell>
                      {record.subjectCode} — {record.subjectName}
                    </TableCell>
                    <TableCell>
                      {record.marksObtained} / {record.maximumMarks}
                    </TableCell>
                    <TableCell>{formatNullable(record.grade)}</TableCell>
                    <TableCell className="text-muted-foreground">{record.remarks || "—"}</TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
          {records && (
            <PaginationBar
              page={records.page}
              size={records.size}
              totalElements={records.totalElements}
              totalPages={records.totalPages}
              onPageChange={setPage}
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}
