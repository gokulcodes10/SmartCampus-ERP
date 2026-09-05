import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { Alert, AlertDescription } from "@/components/ui/alert";
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
import type { CourseRequest, CourseResponse, DepartmentResponse } from "@/types/academic";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const FORM_FIELDS = ["code", "name", "departmentId", "durationSemesters"] as const;
const ALL_DEPARTMENTS = "all";

interface FormState {
  code: string;
  name: string;
  departmentId: string;
  durationSemesters: string;
}

const EMPTY_FORM: FormState = { code: "", name: "", departmentId: "", durationSemesters: "8" };

export default function CoursesPage() {
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [departmentsError, setDepartmentsError] = useState<string | null>(null);
  const [filterDepartmentId, setFilterDepartmentId] = useState<string>(ALL_DEPARTMENTS);

  useEffect(() => {
    departmentService
      .listAllDepartments()
      .then(setDepartments)
      .catch((err) => setDepartmentsError(extractErrorMessage(err, "Failed to load departments.")));
  }, []);

  const filters = useMemo(
    () => (filterDepartmentId === ALL_DEPARTMENTS ? {} : { departmentId: Number(filterDepartmentId) }),
    [filterDepartmentId],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    courseService.listCourses,
    filters,
    { sort: "name,asc" },
  );

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<CourseResponse | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<CourseResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(course: CourseResponse) {
    setEditing(course);
    setForm({
      code: course.code,
      name: course.name,
      departmentId: String(course.departmentId),
      durationSemesters: String(course.durationSemesters),
    });
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.code.trim()) errors.code = "Code is required.";
    if (!form.name.trim()) errors.name = "Name is required.";
    if (!form.departmentId) errors.departmentId = "Department is required.";
    const duration = Number(form.durationSemesters);
    if (!form.durationSemesters || !Number.isInteger(duration) || duration <= 0) {
      errors.durationSemesters = "Duration must be a positive whole number of semesters.";
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    const payload: CourseRequest = {
      code: form.code.trim(),
      name: form.name.trim(),
      departmentId: Number(form.departmentId),
      durationSemesters: Number(form.durationSemesters),
    };
    setIsSaving(true);
    try {
      if (editing) {
        await courseService.updateCourse(editing.id, payload);
      } else {
        await courseService.createCourse(payload);
      }
      setDialogOpen(false);
      refresh();
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, FORM_FIELDS);
        setFieldErrors(parsed.fieldErrors);
        setFormError(parsed.formError);
      } else {
        setFormError(extractErrorMessage(err, "Failed to save the course."));
      }
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await courseService.deleteCourse(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(
        extractErrorMessage(
          err,
          "Failed to delete this course. It may still have subjects or students assigned to it.",
        ),
      );
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Courses</h1>
          <p className="text-muted-foreground">Programs offered by each department.</p>
        </div>
        <Button onClick={openCreate} disabled={departments.length === 0}>
          <PlusIcon />
          Add course
        </Button>
      </div>

      {departmentsError && (
        <Alert variant="destructive">
          <AlertDescription>{departmentsError}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>All courses</CardTitle>
          <CardDescription>Search by code or name, or filter by department.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search courses…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
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
                <TableHead>Code</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Department</TableHead>
                <TableHead>Duration</TableHead>
                <TableHead className="text-right">Actions</TableHead>
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
                    No courses found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((course) => (
                  <TableRow key={course.id}>
                    <TableCell className="font-medium">{course.code}</TableCell>
                    <TableCell>{course.name}</TableCell>
                    <TableCell>{course.departmentName}</TableCell>
                    <TableCell>{course.durationSemesters} semesters</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(course)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(course);
                          }}
                        >
                          <Trash2Icon />
                          <span className="sr-only">Delete</span>
                        </Button>
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

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit course" : "Add course"}</DialogTitle>
              <DialogDescription>
                {editing ? `Update ${editing.name}.` : "Create a new course under a department."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="course-code">Code</Label>
                <Input
                  id="course-code"
                  value={form.code}
                  maxLength={20}
                  onChange={(event) => setForm((f) => ({ ...f, code: event.target.value }))}
                  aria-invalid={!!fieldErrors.code}
                  placeholder="BTECH-CSE"
                />
                {fieldErrors.code && <p className="text-xs text-destructive">{fieldErrors.code}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="course-name">Name</Label>
                <Input
                  id="course-name"
                  value={form.name}
                  maxLength={150}
                  onChange={(event) => setForm((f) => ({ ...f, name: event.target.value }))}
                  aria-invalid={!!fieldErrors.name}
                  placeholder="B.Tech Computer Science"
                />
                {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="course-department">Department</Label>
                <Select
                  value={form.departmentId}
                  onValueChange={(value) => setForm((f) => ({ ...f, departmentId: value ?? "" }))}
                >
                  <SelectTrigger id="course-department" className="w-full" aria-invalid={!!fieldErrors.departmentId}>
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
                {fieldErrors.departmentId && (
                  <p className="text-xs text-destructive">{fieldErrors.departmentId}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="course-duration">Duration (semesters)</Label>
                <Input
                  id="course-duration"
                  type="number"
                  min={1}
                  value={form.durationSemesters}
                  onChange={(event) => setForm((f) => ({ ...f, durationSemesters: event.target.value }))}
                  aria-invalid={!!fieldErrors.durationSemesters}
                />
                {fieldErrors.durationSemesters && (
                  <p className="text-xs text-destructive">{fieldErrors.durationSemesters}</p>
                )}
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create course"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete course?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes <strong>{deleteTarget?.name}</strong>. Courses with subjects
                or students attached cannot be deleted.
              </>
            )}
          </>
        }
        confirmLabel="Delete"
        destructive
        isConfirming={isDeleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
