import { useEffect, useState } from "react";
import { CheckCheckIcon, RefreshCwIcon } from "lucide-react";

import { ClassScopePicker } from "@/components/academics/ClassScopePicker";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as attendanceService from "@/services/attendanceService";
import { ATTENDANCE_STATUSES } from "@/services/attendanceService";
import type {
  AttendanceClassSummaryResponse,
  AttendanceMarkEntry,
  AttendanceRosterResponse,
  AttendanceStatus,
  TeachingClassResponse,
} from "@/types/academicOps";
import { extractErrorMessage } from "@/utils/apiError";

type View = "mark" | "summary";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

const STATUS_LABELS: Record<AttendanceStatus, string> = {
  PRESENT: "Present",
  ABSENT: "Absent",
  LATE: "Late",
  ON_DUTY: "On duty",
  CANCELLED: "Cancelled",
};

function formatPercentage(value: number | null): string {
  return value === null ? "No classes held" : `${value.toFixed(2)}%`;
}

export default function FacultyAttendancePage() {
  const [view, setView] = useState<View>("mark");
  const [selectedClass, setSelectedClass] = useState<TeachingClassResponse | null>(null);
  const [date, setDate] = useState(todayIso());
  const [period, setPeriod] = useState("1");

  const [roster, setRoster] = useState<AttendanceRosterResponse | null>(null);
  const [rosterError, setRosterError] = useState<string | null>(null);
  const [isLoadingRoster, setIsLoadingRoster] = useState(false);

  const [statusByStudent, setStatusByStudent] = useState<Record<number, AttendanceStatus | null>>({});
  const [remarksByStudent, setRemarksByStudent] = useState<Record<number, string>>({});

  const [saveResult, setSaveResult] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [summary, setSummary] = useState<AttendanceClassSummaryResponse | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [isLoadingSummary, setIsLoadingSummary] = useState(false);

  async function loadRoster() {
    if (!selectedClass) return;
    const periodNumber = Number(period);
    if (!date || !Number.isInteger(periodNumber) || periodNumber < 1 || periodNumber > 12) {
      setRosterError("Enter a date and a period between 1 and 12.");
      return;
    }
    setIsLoadingRoster(true);
    setRosterError(null);
    setSaveResult(null);
    setSaveError(null);
    try {
      const result = await attendanceService.getRoster({
        subjectId: selectedClass.subjectId,
        academicYear: selectedClass.academicYear,
        semester: selectedClass.semester,
        section: selectedClass.section,
        date,
        period: periodNumber,
      });
      setRoster(result);
      const statuses: Record<number, AttendanceStatus | null> = {};
      const remarks: Record<number, string> = {};
      for (const entry of result.entries) {
        statuses[entry.studentId] = entry.status;
        remarks[entry.studentId] = entry.remarks ?? "";
      }
      setStatusByStudent(statuses);
      setRemarksByStudent(remarks);
    } catch (err) {
      setRoster(null);
      setRosterError(extractErrorMessage(err, "Failed to load the class roster."));
    } finally {
      setIsLoadingRoster(false);
    }
  }

  async function loadSummary() {
    if (!selectedClass) return;
    setIsLoadingSummary(true);
    setSummaryError(null);
    try {
      const result = await attendanceService.getClassSummary({
        subjectId: selectedClass.subjectId,
        academicYear: selectedClass.academicYear,
        semester: selectedClass.semester,
        section: selectedClass.section,
      });
      setSummary(result);
    } catch (err) {
      setSummary(null);
      setSummaryError(extractErrorMessage(err, "Failed to load the attendance summary."));
    } finally {
      setIsLoadingSummary(false);
    }
  }

  // Reset the roster/summary whenever the selected class changes — stale data from a
  // different subject must never linger on screen.
  useEffect(() => {
    setRoster(null);
    setSummary(null);
    setRosterError(null);
    setSummaryError(null);
    setSaveResult(null);
    setSaveError(null);
  }, [selectedClass?.assignmentId]);

  useEffect(() => {
    if (view === "summary" && selectedClass && !summary && !isLoadingSummary) {
      loadSummary();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, selectedClass?.assignmentId]);

  function markAllPresent() {
    if (!roster) return;
    const next: Record<number, AttendanceStatus | null> = {};
    for (const entry of roster.entries) {
      next[entry.studentId] = "PRESENT";
    }
    setStatusByStudent(next);
  }

  async function handleSubmit() {
    if (!roster || !selectedClass) return;
    setSaveError(null);
    setSaveResult(null);

    const entries: AttendanceMarkEntry[] = [];
    for (const rosterEntry of roster.entries) {
      const status = statusByStudent[rosterEntry.studentId];
      if (!status) {
        setSaveError(`Select a status for ${rosterEntry.studentName} before submitting.`);
        return;
      }
      const remarks = remarksByStudent[rosterEntry.studentId]?.trim();
      entries.push({ studentId: rosterEntry.studentId, status, remarks: remarks || null });
    }

    setIsSaving(true);
    try {
      const result = await attendanceService.markBulk({
        subjectId: selectedClass.subjectId,
        academicYear: selectedClass.academicYear,
        semester: selectedClass.semester,
        section: selectedClass.section,
        date: roster.date,
        period: roster.period,
        entries,
      });
      setSaveResult(`Saved: ${result.createdCount} created, ${result.updatedCount} updated.`);
      await loadRoster();
      setSummary(null);
    } catch (err) {
      setSaveError(extractErrorMessage(err, "Failed to save attendance."));
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Attendance</h1>
        <p className="text-muted-foreground">Mark attendance for a period and review class totals.</p>
      </div>

      <Card>
        <CardContent className="pt-6">
          <ClassScopePicker value={selectedClass} onChange={setSelectedClass} />
        </CardContent>
      </Card>

      {selectedClass && (
        <div className="flex gap-2">
          <Button
            type="button"
            variant={view === "mark" ? "default" : "outline"}
            size="sm"
            onClick={() => setView("mark")}
          >
            Mark attendance
          </Button>
          <Button
            type="button"
            variant={view === "summary" ? "default" : "outline"}
            size="sm"
            onClick={() => setView("summary")}
          >
            Class summary
          </Button>
        </div>
      )}

      {selectedClass && view === "mark" && (
        <Card>
          <CardHeader>
            <CardTitle>Mark attendance</CardTitle>
            <CardDescription>
              {selectedClass.subjectCode} — {selectedClass.subjectName} · {selectedClass.academicYear} Sem{" "}
              {selectedClass.semester} Sec {selectedClass.section}
            </CardDescription>
            <div className="flex flex-wrap items-end gap-3 pt-2">
              <div className="space-y-1.5">
                <Label htmlFor="attendance-date">Date</Label>
                <Input
                  id="attendance-date"
                  type="date"
                  value={date}
                  max={todayIso()}
                  onChange={(event) => setDate(event.target.value)}
                  className="w-44"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="attendance-period">Period</Label>
                <Input
                  id="attendance-period"
                  type="number"
                  min={1}
                  max={12}
                  value={period}
                  onChange={(event) => setPeriod(event.target.value)}
                  className="w-24"
                />
              </div>
              <Button type="button" onClick={loadRoster} disabled={isLoadingRoster}>
                <RefreshCwIcon />
                {isLoadingRoster ? "Loading…" : "Load roster"}
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {rosterError && (
              <Alert variant="destructive">
                <AlertDescription>{rosterError}</AlertDescription>
              </Alert>
            )}
            {saveError && (
              <Alert variant="destructive">
                <AlertDescription>{saveError}</AlertDescription>
              </Alert>
            )}
            {saveResult && (
              <Alert>
                <AlertDescription>{saveResult}</AlertDescription>
              </Alert>
            )}

            {roster && (
              <>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm text-muted-foreground">
                    {roster.entries.length} student{roster.entries.length === 1 ? "" : "s"}
                    {roster.alreadyMarked ? " · already marked for this period" : ""}
                  </p>
                  <Button type="button" variant="outline" size="sm" onClick={markAllPresent}>
                    <CheckCheckIcon />
                    Mark all present
                  </Button>
                </div>

                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Register no.</TableHead>
                      <TableHead>Student</TableHead>
                      <TableHead className="w-44">Status</TableHead>
                      <TableHead>Remarks</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {roster.entries.map((entry) => (
                      <TableRow key={entry.studentId}>
                        <TableCell className="font-medium">{entry.registerNumber ?? "—"}</TableCell>
                        <TableCell>{entry.studentName}</TableCell>
                        <TableCell>
                          <Select
                            value={statusByStudent[entry.studentId] ?? null}
                            onValueChange={(value) =>
                              setStatusByStudent((prev) => ({
                                ...prev,
                                [entry.studentId]: (value as AttendanceStatus) ?? null,
                              }))
                            }
                          >
                            <SelectTrigger className="w-40">
                              <SelectValue placeholder="Select status" />
                            </SelectTrigger>
                            <SelectContent>
                              {ATTENDANCE_STATUSES.map((status) => (
                                <SelectItem key={status} value={status}>
                                  {STATUS_LABELS[status]}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        </TableCell>
                        <TableCell>
                          <Input
                            value={remarksByStudent[entry.studentId] ?? ""}
                            maxLength={255}
                            placeholder="Optional"
                            onChange={(event) =>
                              setRemarksByStudent((prev) => ({
                                ...prev,
                                [entry.studentId]: event.target.value,
                              }))
                            }
                          />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>

                <div className="flex justify-end">
                  <Button type="button" onClick={handleSubmit} disabled={isSaving}>
                    {isSaving ? "Saving…" : "Save attendance"}
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}

      {selectedClass && view === "summary" && (
        <Card>
          <CardHeader>
            <CardTitle>Class summary</CardTitle>
            <CardDescription>
              {selectedClass.subjectCode} — {selectedClass.subjectName} · {selectedClass.academicYear} Sem{" "}
              {selectedClass.semester} Sec {selectedClass.section}
            </CardDescription>
            <div className="pt-2">
              <Button type="button" variant="outline" size="sm" onClick={loadSummary} disabled={isLoadingSummary}>
                <RefreshCwIcon />
                {isLoadingSummary ? "Loading…" : "Refresh"}
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            {summaryError && (
              <Alert variant="destructive">
                <AlertDescription>{summaryError}</AlertDescription>
              </Alert>
            )}

            {summary && (
              <>
                <p className="text-sm text-muted-foreground">
                  Minimum required attendance: {summary.minimumPercentage}% · {summary.lowAttendanceCount} of{" "}
                  {summary.studentCount} student{summary.studentCount === 1 ? "" : "s"} below threshold
                </p>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Register no.</TableHead>
                      <TableHead>Student</TableHead>
                      <TableHead>Held</TableHead>
                      <TableHead>Attended</TableHead>
                      <TableHead>Cancelled</TableHead>
                      <TableHead>Percentage</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {summary.entries.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                          No attendance records yet.
                        </TableCell>
                      </TableRow>
                    )}
                    {summary.entries.map((entry) => (
                      <TableRow key={entry.studentId}>
                        <TableCell className="font-medium">{entry.registerNumber ?? "—"}</TableCell>
                        <TableCell>{entry.studentName}</TableCell>
                        <TableCell>{entry.heldClasses}</TableCell>
                        <TableCell>{entry.attendedClasses}</TableCell>
                        <TableCell>{entry.cancelledClasses}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <span>{formatPercentage(entry.attendancePercentage)}</span>
                            {entry.lowAttendance && <Badge variant="destructive">Low</Badge>}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
