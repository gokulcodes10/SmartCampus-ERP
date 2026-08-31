import { useEffect, useState } from "react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as attendanceService from "@/services/attendanceService";
import type { Page } from "@/types/academic";
import type { AttendanceResponse, AttendanceSummaryResponse } from "@/types/academicOps";
import { extractErrorMessage } from "@/utils/apiError";

const RECORDS_PAGE_SIZE = 20;

/** Renders a nullable percentage honestly — G6: null means no classes were held, never 0%. */
function formatPercentage(value: number | null): string {
  return value === null ? "No classes held" : `${value.toFixed(2)}%`;
}

export default function StudentAttendancePage() {
  const [academicYear, setAcademicYear] = useState("");
  const [semester, setSemester] = useState("");

  const [summary, setSummary] = useState<AttendanceSummaryResponse | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [records, setRecords] = useState<Page<AttendanceResponse> | null>(null);
  const [recordsLoading, setRecordsLoading] = useState(true);
  const [recordsError, setRecordsError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const filters = {
    academicYear: academicYear.trim() || undefined,
    semester: semester.trim() ? Number(semester) : undefined,
  };

  useEffect(() => {
    let cancelled = false;
    setSummaryLoading(true);
    setSummaryError(null);
    attendanceService
      .getMySummary(filters)
      .then((result) => {
        if (!cancelled) setSummary(result);
      })
      .catch((err) => {
        if (!cancelled) setSummaryError(extractErrorMessage(err, "Failed to load your attendance summary."));
      })
      .finally(() => {
        if (!cancelled) setSummaryLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [academicYear, semester]);

  useEffect(() => {
    let cancelled = false;
    setRecordsLoading(true);
    setRecordsError(null);
    attendanceService
      .listMine({ ...filters, page, size: RECORDS_PAGE_SIZE })
      .then((result) => {
        if (!cancelled) setRecords(result);
      })
      .catch((err) => {
        if (!cancelled) setRecordsError(extractErrorMessage(err, "Failed to load your attendance records."));
      })
      .finally(() => {
        if (!cancelled) setRecordsLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [academicYear, semester, page]);

  useEffect(() => {
    setPage(0);
  }, [academicYear, semester]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">My attendance</h1>
        <p className="text-muted-foreground">
          Your attendance across every subject. Classes marked CANCELLED never count toward the total.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Leave blank to see every academic year and semester.</CardDescription>
          <div className="flex flex-wrap items-end gap-3 pt-2">
            <div className="space-y-1.5">
              <Label htmlFor="filter-year">Academic year</Label>
              <Input
                id="filter-year"
                placeholder="2025-2026"
                value={academicYear}
                onChange={(event) => setAcademicYear(event.target.value)}
                className="w-40"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="filter-semester">Semester</Label>
              <Input
                id="filter-semester"
                type="number"
                min={1}
                placeholder="All"
                value={semester}
                onChange={(event) => setSemester(event.target.value)}
                className="w-28"
              />
            </div>
          </div>
        </CardHeader>
      </Card>

      {summaryError && (
        <Alert variant="destructive">
          <AlertDescription>{summaryError}</AlertDescription>
        </Alert>
      )}

      {summaryLoading && (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">Loading summary…</CardContent>
        </Card>
      )}

      {!summaryLoading && summary && (
        <Card>
          <CardHeader>
            <CardTitle>Overall</CardTitle>
            <CardDescription>
              Minimum required attendance is {summary.minimumPercentage}%.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex flex-wrap items-center gap-3">
              <span className="text-3xl font-semibold tracking-tight">
                {formatPercentage(summary.overallPercentage)}
              </span>
              {summary.lowAttendance && <Badge variant="destructive">Below minimum</Badge>}
            </div>
            <div className="grid grid-cols-3 gap-4 text-sm sm:grid-cols-4">
              <div>
                <p className="text-muted-foreground">Held</p>
                <p className="font-medium">{summary.heldClasses}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Attended</p>
                <p className="font-medium">{summary.attendedClasses}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Cancelled</p>
                <p className="font-medium">{summary.cancelledClasses}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Total records</p>
                <p className="font-medium">{summary.totalRecords}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {!summaryLoading && summary && (
        <Card>
          <CardHeader>
            <CardTitle>By subject</CardTitle>
          </CardHeader>
          <CardContent className="px-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Subject</TableHead>
                  <TableHead>Year / Sem</TableHead>
                  <TableHead>Held</TableHead>
                  <TableHead>Attended</TableHead>
                  <TableHead>Cancelled</TableHead>
                  <TableHead>Percentage</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {summary.subjects.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                      No attendance recorded yet.
                    </TableCell>
                  </TableRow>
                )}
                {summary.subjects.map((subject) => (
                  <TableRow key={`${subject.subjectId}-${subject.academicYear}-${subject.semester}`}>
                    <TableCell className="font-medium">
                      {subject.subjectCode} — {subject.subjectName}
                    </TableCell>
                    <TableCell>
                      {subject.academicYear} / Sem {subject.semester}
                    </TableCell>
                    <TableCell>{subject.heldClasses}</TableCell>
                    <TableCell>{subject.attendedClasses}</TableCell>
                    <TableCell>{subject.cancelledClasses}</TableCell>
                    <TableCell>{formatPercentage(subject.attendancePercentage)}</TableCell>
                    <TableCell>
                      {subject.lowAttendance ? (
                        <Badge variant="destructive">Low</Badge>
                      ) : subject.attendancePercentage === null ? (
                        <Badge variant="secondary">No data</Badge>
                      ) : (
                        <Badge variant="default">OK</Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Records</CardTitle>
          <CardDescription>Every individual attendance entry, most recent first.</CardDescription>
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
                <TableHead>Date</TableHead>
                <TableHead>Period</TableHead>
                <TableHead>Subject</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Remarks</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {recordsLoading && (
                <TableRow>
                  <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!recordsLoading && records?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                    No records found.
                  </TableCell>
                </TableRow>
              )}
              {!recordsLoading &&
                records?.content.map((record) => (
                  <TableRow key={record.id}>
                    <TableCell>{record.date}</TableCell>
                    <TableCell>{record.period}</TableCell>
                    <TableCell>
                      {record.subjectCode} — {record.subjectName}
                    </TableCell>
                    <TableCell>{record.status}</TableCell>
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
