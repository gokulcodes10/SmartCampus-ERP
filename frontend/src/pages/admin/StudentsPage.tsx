import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { CheckCircle2Icon, PencilIcon, PowerIcon, PowerOffIcon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { useServerTable } from "@/hooks/useServerTable";
import * as courseService from "@/services/courseService";
import * as departmentService from "@/services/departmentService";
import * as studentService from "@/services/studentService";
import type {
  CourseResponse,
  DepartmentResponse,
  StudentResponse,
  StudentStatus,
} from "@/types/academic";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const ALL_DEPARTMENTS = "all";
const ALL_STATUSES = "all";
const ASSIGN_FIELDS = ["departmentId", "courseId", "registerNumber", "currentSemester", "section", "admissionYear"] as const;

function statusBadgeVariant(status: StudentStatus) {
  if (status === "ACTIVE") return "default";
  if (status === "PENDING") return "outline";
  return "secondary";
}

/** Fetches the course list for one department, re-fetching whenever it changes. */
function useCoursesForDepartment(departmentId: number | null) {
  const [courses, setCourses] = useState<CourseResponse[]>([]);
  // Reset to [] during render as soon as `departmentId` changes (covers the null
  // case too), rather than as a synchronous setState at the top of the effect — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes.
  const [fetchedFor, setFetchedFor] = useState<number | null | undefined>(undefined);
  if (departmentId !== fetchedFor) {
    setFetchedFor(departmentId);
    setCourses([]);
  }
  useEffect(() => {
    if (departmentId === null) return;
    let cancelled = false;
    courseService.listAllCourses(departmentId).then((result) => {
      if (!cancelled) setCourses(result);
    });
    return () => {
      cancelled = true;
    };
  }, [departmentId]);
  return courses;
}

interface AssignFormState {
  departmentId: string;
  courseId: string;
  registerNumber: string;
  currentSemester: string;
  section: string;
  admissionYear: string;
}

const EMPTY_ASSIGN_FORM: AssignFormState = {
  departmentId: "",
  courseId: "",
  registerNumber: "",
  currentSemester: "",
  section: "",
  admissionYear: "",
};

export default function StudentsPage() {
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [departmentsError, setDepartmentsError] = useState<string | null>(null);
  const [filterDepartmentId, setFilterDepartmentId] = useState<string>(ALL_DEPARTMENTS);
  const [filterStatus, setFilterStatus] = useState<string>(ALL_STATUSES);

  useEffect(() => {
    departmentService
      .listAllDepartments()
      .then(setDepartments)
      .catch((err) => setDepartmentsError(extractErrorMessage(err, "Failed to load departments.")));
  }, []);

  const filters = useMemo(
    () => ({
      ...(filterDepartmentId === ALL_DEPARTMENTS ? {} : { departmentId: Number(filterDepartmentId) }),
      ...(filterStatus === ALL_STATUSES ? {} : { status: filterStatus as StudentStatus }),
    }),
    [filterDepartmentId, filterStatus],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    studentService.listStudents,
    filters,
    { sort: "user.fullName,asc" },
  );

  const pendingCount = data?.content.filter((s) => s.status === "PENDING").length ?? 0;

  // Activate (G1)
  const [activating, setActivating] = useState<StudentResponse | null>(null);
  const [activateForm, setActivateForm] = useState<AssignFormState>(EMPTY_ASSIGN_FORM);
  const [activateFieldErrors, setActivateFieldErrors] = useState<Record<string, string>>({});
  const [activateFormError, setActivateFormError] = useState<string | null>(null);
  const [isActivating, setIsActivating] = useState(false);
  const activateDeptId = activateForm.departmentId ? Number(activateForm.departmentId) : null;
  const activateCourses = useCoursesForDepartment(activateDeptId);

  // Edit (already-assigned students)
  const [editing, setEditing] = useState<StudentResponse | null>(null);
  const [editForm, setEditForm] = useState<AssignFormState>(EMPTY_ASSIGN_FORM);
  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>({});
  const [editFormError, setEditFormError] = useState<string | null>(null);
  const [isSavingEdit, setIsSavingEdit] = useState(false);
  const editDeptId = editForm.departmentId ? Number(editForm.departmentId) : null;
  const editCourses = useCoursesForDepartment(editDeptId);

  // Status toggle
  const [statusTarget, setStatusTarget] = useState<StudentResponse | null>(null);
  const [isTogglingStatus, setIsTogglingStatus] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);

  function openActivate(student: StudentResponse) {
    setActivating(student);
    setActivateForm(EMPTY_ASSIGN_FORM);
    setActivateFieldErrors({});
    setActivateFormError(null);
  }

  function validateAssignment(form: AssignFormState): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!form.departmentId) errors.departmentId = "Department is required.";
    if (!form.courseId) errors.courseId = "Course is required.";
    if (!form.registerNumber.trim()) errors.registerNumber = "Register number is required.";
    const semester = Number(form.currentSemester);
    if (!form.currentSemester || !Number.isInteger(semester) || semester <= 0) {
      errors.currentSemester = "Semester must be a positive whole number.";
    }
    if (!form.section.trim()) errors.section = "Section is required.";
    if (form.admissionYear) {
      const year = Number(form.admissionYear);
      if (!Number.isInteger(year) || year < 1900) errors.admissionYear = "Enter a valid year.";
    }
    return errors;
  }

  async function handleActivateSubmit(event: FormEvent) {
    event.preventDefault();
    if (!activating) return;
    setActivateFormError(null);
    const errors = validateAssignment(activateForm);
    setActivateFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsActivating(true);
    try {
      await studentService.activateStudent(activating.id, {
        departmentId: Number(activateForm.departmentId),
        courseId: Number(activateForm.courseId),
        registerNumber: activateForm.registerNumber.trim(),
        currentSemester: Number(activateForm.currentSemester),
        section: activateForm.section.trim(),
        admissionYear: activateForm.admissionYear ? Number(activateForm.admissionYear) : undefined,
      });
      setActivating(null);
      refresh();
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, ASSIGN_FIELDS);
        setActivateFieldErrors(parsed.fieldErrors);
        setActivateFormError(parsed.formError);
      } else {
        setActivateFormError(extractErrorMessage(err, "Failed to activate this student."));
      }
    } finally {
      setIsActivating(false);
    }
  }

  function openEdit(student: StudentResponse) {
    setEditing(student);
    setEditForm({
      departmentId: student.departmentId ? String(student.departmentId) : "",
      courseId: student.courseId ? String(student.courseId) : "",
      registerNumber: student.registerNumber ?? "",
      currentSemester: student.currentSemester ? String(student.currentSemester) : "",
      section: student.section ?? "",
      admissionYear: student.admissionYear ? String(student.admissionYear) : "",
    });
    setEditFieldErrors({});
    setEditFormError(null);
  }

  async function handleEditSubmit(event: FormEvent) {
    event.preventDefault();
    if (!editing) return;
    setEditFormError(null);

    // INACTIVE students may be edited with fields left blank (e.g. an alumni record);
    // only enforce the full-assignment rule when the student is (or will stay) ACTIVE.
    const errors = editing.status === "INACTIVE" ? {} : validateAssignment(editForm);
    setEditFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSavingEdit(true);
    try {
      // Register number and status are deliberately not sent here — the backend's
      // StudentAdminUpdateRequest has no such fields (they can only change via
      // activate/deactivate/reactivate, which keep the CHECK constraint satisfiable).
      await studentService.updateStudent(editing.id, {
        departmentId: editForm.departmentId ? Number(editForm.departmentId) : null,
        courseId: editForm.courseId ? Number(editForm.courseId) : null,
        currentSemester: editForm.currentSemester ? Number(editForm.currentSemester) : null,
        section: editForm.section.trim() || null,
        admissionYear: editForm.admissionYear ? Number(editForm.admissionYear) : null,
      });
      setEditing(null);
      refresh();
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, ASSIGN_FIELDS);
        setEditFieldErrors(parsed.fieldErrors);
        setEditFormError(parsed.formError);
      } else {
        setEditFormError(extractErrorMessage(err, "Failed to save student details."));
      }
    } finally {
      setIsSavingEdit(false);
    }
  }

  async function handleToggleStatus() {
    if (!statusTarget) return;
    setIsTogglingStatus(true);
    setStatusError(null);
    try {
      // Deactivate/reactivate are dedicated PATCH routes, not a `status` field on the
      // PUT payload — StudentAdminUpdateRequest doesn't have one (see studentService.ts).
      if (statusTarget.status === "ACTIVE") {
        await studentService.deactivateStudent(statusTarget.id);
      } else {
        await studentService.reactivateStudent(statusTarget.id);
      }
      setStatusTarget(null);
      refresh();
    } catch (err) {
      setStatusError(extractErrorMessage(err, "Failed to update student status."));
    } finally {
      setIsTogglingStatus(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Students</h1>
        <p className="text-muted-foreground">
          Every self-registered student. New sign-ups start PENDING until activated (G1).
        </p>
      </div>

      {departmentsError && (
        <Alert variant="destructive">
          <AlertDescription>{departmentsError}</AlertDescription>
        </Alert>
      )}

      {filterStatus !== "PENDING" && pendingCount > 0 && (
        <Alert>
          <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
            <span>
              {pendingCount} student{pendingCount === 1 ? "" : "s"} on this page still need
              activation.
            </span>
            <Button size="sm" variant="outline" onClick={() => setFilterStatus("PENDING")}>
              View pending queue
            </Button>
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>All students</CardTitle>
          <CardDescription>Search by name, email or register number.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search students…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={filterStatus} onValueChange={(value) => setFilterStatus(value ?? ALL_STATUSES)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
                <SelectItem value="PENDING">Pending activation</SelectItem>
                <SelectItem value="ACTIVE">Active</SelectItem>
                <SelectItem value="INACTIVE">Inactive</SelectItem>
              </SelectContent>
            </Select>
            <Select value={filterDepartmentId} onValueChange={(value) => setFilterDepartmentId(value ?? ALL_DEPARTMENTS)}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="All departments" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_DEPARTMENTS}>All departments</SelectItem>
                {departments.map((department) => (
                  <SelectItem key={department.id} value={String(department.id)}>
                    {department.name}
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
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Register No.</TableHead>
                <TableHead>Department / Course</TableHead>
                <TableHead>Sem / Sec</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    Loading…
                  </TableCell>
                </TableRow>
              )}
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    No students found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((student) => (
                  <TableRow key={student.id}>
                    <TableCell className="font-medium">{student.fullName}</TableCell>
                    <TableCell className="text-muted-foreground">{student.email}</TableCell>
                    <TableCell>{student.registerNumber ?? "—"}</TableCell>
                    <TableCell>
                      {student.departmentName && student.courseName
                        ? `${student.departmentName} / ${student.courseName}`
                        : "—"}
                    </TableCell>
                    <TableCell>
                      {student.currentSemester ?? "—"} / {student.section ?? "—"}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusBadgeVariant(student.status)}>{student.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        {student.status === "PENDING" ? (
                          <Button size="sm" onClick={() => openActivate(student)}>
                            <CheckCircle2Icon />
                            Activate
                          </Button>
                        ) : (
                          <>
                            <Button variant="ghost" size="icon" onClick={() => openEdit(student)}>
                              <PencilIcon />
                              <span className="sr-only">Edit</span>
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => {
                                setStatusError(null);
                                setStatusTarget(student);
                              }}
                            >
                              {student.status === "ACTIVE" ? <PowerOffIcon /> : <PowerIcon />}
                              <span className="sr-only">
                                {student.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
                              </span>
                            </Button>
                          </>
                        )}
                      </div>
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

      {/* G1: activate a pending student by assigning department, course and register number */}
      <Dialog open={activating !== null} onOpenChange={(open) => !open && setActivating(null)}>
        <DialogContent>
          <form onSubmit={handleActivateSubmit}>
            <DialogHeader>
              <DialogTitle>Activate student</DialogTitle>
              <DialogDescription>
                {activating
                  ? `Assign ${activating.fullName} to a department, course and register number to activate their account.`
                  : ""}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {activateFormError && (
                <Alert variant="destructive">
                  <AlertDescription>{activateFormError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="activate-department">Department</Label>
                <Select
                  value={activateForm.departmentId}
                  onValueChange={(value) =>
                    setActivateForm((f) => ({ ...f, departmentId: value ?? "", courseId: "" }))
                  }
                >
                  <SelectTrigger
                    id="activate-department"
                    className="w-full"
                    aria-invalid={!!activateFieldErrors.departmentId}
                  >
                    <SelectValue placeholder="Select a department" />
                  </SelectTrigger>
                  <SelectContent>
                    {departments.map((department) => (
                      <SelectItem key={department.id} value={String(department.id)}>
                        {department.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {activateFieldErrors.departmentId && (
                  <p className="text-xs text-destructive">{activateFieldErrors.departmentId}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="activate-course">Course</Label>
                <Select
                  value={activateForm.courseId}
                  onValueChange={(value) => setActivateForm((f) => ({ ...f, courseId: value ?? "" }))}
                  disabled={!activateForm.departmentId}
                >
                  <SelectTrigger
                    id="activate-course"
                    className="w-full"
                    aria-invalid={!!activateFieldErrors.courseId}
                  >
                    <SelectValue placeholder="Select a course" />
                  </SelectTrigger>
                  <SelectContent>
                    {activateCourses.map((course) => (
                      <SelectItem key={course.id} value={String(course.id)}>
                        {course.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {activateFieldErrors.courseId && (
                  <p className="text-xs text-destructive">{activateFieldErrors.courseId}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="activate-register">Register number</Label>
                <Input
                  id="activate-register"
                  value={activateForm.registerNumber}
                  maxLength={20}
                  onChange={(event) =>
                    setActivateForm((f) => ({ ...f, registerNumber: event.target.value }))
                  }
                  aria-invalid={!!activateFieldErrors.registerNumber}
                  placeholder="21CSE1042"
                />
                {activateFieldErrors.registerNumber && (
                  <p className="text-xs text-destructive">{activateFieldErrors.registerNumber}</p>
                )}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="activate-semester">Current semester</Label>
                  <Input
                    id="activate-semester"
                    type="number"
                    min={1}
                    value={activateForm.currentSemester}
                    onChange={(event) =>
                      setActivateForm((f) => ({ ...f, currentSemester: event.target.value }))
                    }
                    aria-invalid={!!activateFieldErrors.currentSemester}
                  />
                  {activateFieldErrors.currentSemester && (
                    <p className="text-xs text-destructive">{activateFieldErrors.currentSemester}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="activate-section">Section</Label>
                  <Input
                    id="activate-section"
                    value={activateForm.section}
                    maxLength={10}
                    onChange={(event) => setActivateForm((f) => ({ ...f, section: event.target.value }))}
                    aria-invalid={!!activateFieldErrors.section}
                    placeholder="A"
                  />
                  {activateFieldErrors.section && (
                    <p className="text-xs text-destructive">{activateFieldErrors.section}</p>
                  )}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="activate-admission-year">Admission year (optional)</Label>
                <Input
                  id="activate-admission-year"
                  type="number"
                  value={activateForm.admissionYear}
                  onChange={(event) =>
                    setActivateForm((f) => ({ ...f, admissionYear: event.target.value }))
                  }
                  aria-invalid={!!activateFieldErrors.admissionYear}
                  placeholder="2024"
                />
                {activateFieldErrors.admissionYear && (
                  <p className="text-xs text-destructive">{activateFieldErrors.admissionYear}</p>
                )}
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setActivating(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isActivating}>
                {isActivating ? "Activating…" : "Activate student"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Edit an already-assigned student */}
      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          <form onSubmit={handleEditSubmit}>
            <DialogHeader>
              <DialogTitle>Edit student</DialogTitle>
              <DialogDescription>
                {editing ? `Update ${editing.fullName}'s academic assignment.` : ""}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {editFormError && (
                <Alert variant="destructive">
                  <AlertDescription>{editFormError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="edit-department">Department</Label>
                <Select
                  value={editForm.departmentId}
                  onValueChange={(value) => setEditForm((f) => ({ ...f, departmentId: value ?? "", courseId: "" }))}
                >
                  <SelectTrigger
                    id="edit-department"
                    className="w-full"
                    aria-invalid={!!editFieldErrors.departmentId}
                  >
                    <SelectValue placeholder="Select a department" />
                  </SelectTrigger>
                  <SelectContent>
                    {departments.map((department) => (
                      <SelectItem key={department.id} value={String(department.id)}>
                        {department.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {editFieldErrors.departmentId && (
                  <p className="text-xs text-destructive">{editFieldErrors.departmentId}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-course">Course</Label>
                <Select
                  value={editForm.courseId}
                  onValueChange={(value) => setEditForm((f) => ({ ...f, courseId: value ?? "" }))}
                  disabled={!editForm.departmentId}
                >
                  <SelectTrigger id="edit-course" className="w-full" aria-invalid={!!editFieldErrors.courseId}>
                    <SelectValue placeholder="Select a course" />
                  </SelectTrigger>
                  <SelectContent>
                    {editCourses.map((course) => (
                      <SelectItem key={course.id} value={String(course.id)}>
                        {course.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {editFieldErrors.courseId && (
                  <p className="text-xs text-destructive">{editFieldErrors.courseId}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-register">Register number</Label>
                <Input id="edit-register" value={editForm.registerNumber} disabled readOnly />
                <p className="text-xs text-muted-foreground">
                  Register number is set once, at activation, and cannot be changed here.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="edit-semester">Current semester</Label>
                  <Input
                    id="edit-semester"
                    type="number"
                    min={1}
                    value={editForm.currentSemester}
                    onChange={(event) => setEditForm((f) => ({ ...f, currentSemester: event.target.value }))}
                    aria-invalid={!!editFieldErrors.currentSemester}
                  />
                  {editFieldErrors.currentSemester && (
                    <p className="text-xs text-destructive">{editFieldErrors.currentSemester}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="edit-section">Section</Label>
                  <Input
                    id="edit-section"
                    value={editForm.section}
                    maxLength={10}
                    onChange={(event) => setEditForm((f) => ({ ...f, section: event.target.value }))}
                    aria-invalid={!!editFieldErrors.section}
                  />
                  {editFieldErrors.section && (
                    <p className="text-xs text-destructive">{editFieldErrors.section}</p>
                  )}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-admission-year">Admission year</Label>
                <Input
                  id="edit-admission-year"
                  type="number"
                  value={editForm.admissionYear}
                  onChange={(event) => setEditForm((f) => ({ ...f, admissionYear: event.target.value }))}
                  aria-invalid={!!editFieldErrors.admissionYear}
                />
                {editFieldErrors.admissionYear && (
                  <p className="text-xs text-destructive">{editFieldErrors.admissionYear}</p>
                )}
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setEditing(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSavingEdit}>
                {isSavingEdit ? "Saving…" : "Save changes"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={statusTarget !== null}
        onOpenChange={(open) => !open && setStatusTarget(null)}
        title={statusTarget?.status === "ACTIVE" ? "Deactivate student?" : "Reactivate student?"}
        description={
          <>
            {statusError ? (
              <span className="text-destructive">{statusError}</span>
            ) : statusTarget?.status === "ACTIVE" ? (
              <>
                <strong>{statusTarget?.fullName}</strong> will lose access to student
                features. Their record is kept and this can be reversed.
              </>
            ) : (
              <>
                <strong>{statusTarget?.fullName}</strong> will regain access to student
                features.
              </>
            )}
          </>
        }
        confirmLabel={statusTarget?.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
        destructive={statusTarget?.status === "ACTIVE"}
        isConfirming={isTogglingStatus}
        onConfirm={handleToggleStatus}
      />
    </div>
  );
}
