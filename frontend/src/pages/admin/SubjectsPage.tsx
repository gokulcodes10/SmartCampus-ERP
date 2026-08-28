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
import * as subjectService from "@/services/subjectService";
import type { CourseResponse, SubjectRequest, SubjectResponse } from "@/types/academic";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const FORM_FIELDS = ["code", "name", "credits", "semester", "courseId"] as const;
const ALL_COURSES = "all";

interface FormState {
  code: string;
  name: string;
  credits: string;
  semester: string;
  courseId: string;
}

const EMPTY_FORM: FormState = { code: "", name: "", credits: "3", semester: "1", courseId: "" };

export default function SubjectsPage() {
  const [courses, setCourses] = useState<CourseResponse[]>([]);
  const [coursesError, setCoursesError] = useState<string | null>(null);
  const [filterCourseId, setFilterCourseId] = useState<string>(ALL_COURSES);

  useEffect(() => {
    courseService
      .listAllCourses()
      .then(setCourses)
      .catch((err) => setCoursesError(extractErrorMessage(err, "Failed to load courses.")));
  }, []);

  const filters = useMemo(
    () => (filterCourseId === ALL_COURSES ? {} : { courseId: Number(filterCourseId) }),
    [filterCourseId],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    subjectService.listSubjects,
    filters,
    { sort: "semester,asc" },
  );

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<SubjectResponse | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<SubjectResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(subject: SubjectResponse) {
    setEditing(subject);
    setForm({
      code: subject.code,
      name: subject.name,
      credits: String(subject.credits),
      semester: String(subject.semester),
      courseId: String(subject.courseId),
    });
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.code.trim()) errors.code = "Code is required.";
    if (!form.name.trim()) errors.name = "Name is required.";
    if (!form.courseId) errors.courseId = "Course is required.";

    const credits = Number(form.credits);
    if (!form.credits || !Number.isInteger(credits) || credits < 1 || credits > 10) {
      errors.credits = "Credits must be a whole number from 1 to 10.";
    }

    const semester = Number(form.semester);
    if (!form.semester || !Number.isInteger(semester) || semester <= 0) {
      errors.semester = "Semester must be a positive whole number.";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    const payload: SubjectRequest = {
      code: form.code.trim(),
      name: form.name.trim(),
      credits: Number(form.credits),
      semester: Number(form.semester),
      courseId: Number(form.courseId),
    };
    setIsSaving(true);
    try {
      if (editing) {
        await subjectService.updateSubject(editing.id, payload);
      } else {
        await subjectService.createSubject(payload);
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
        setFormError(extractErrorMessage(err, "Failed to save the subject."));
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
      await subjectService.deleteSubject(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(
        extractErrorMessage(
          err,
          "Failed to delete this subject. It may still have enrollments or faculty assignments.",
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
          <h1 className="text-2xl font-semibold tracking-tight">Subjects</h1>
          <p className="text-muted-foreground">Syllabus subjects taught within each course.</p>
        </div>
        <Button onClick={openCreate} disabled={courses.length === 0}>
          <PlusIcon />
          Add subject
        </Button>
      </div>

      {coursesError && (
        <Alert variant="destructive">
          <AlertDescription>{coursesError}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>All subjects</CardTitle>
          <CardDescription>Search by code or name, or filter by course.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search subjects…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={filterCourseId} onValueChange={(value) => setFilterCourseId(value ?? ALL_COURSES)}>
              <SelectTrigger className="w-56">
                <SelectValue placeholder="All courses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_COURSES}>All courses</SelectItem>
                {courses.map((course) => (
                  <SelectItem key={course.id} value={String(course.id)}>
                    {course.name}
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
                <TableHead>Course</TableHead>
                <TableHead>Semester</TableHead>
                <TableHead>Credits</TableHead>
                <TableHead className="text-right">Actions</TableHead>
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
              {!isLoading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    No subjects found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((subject) => (
                  <TableRow key={subject.id}>
                    <TableCell className="font-medium">{subject.code}</TableCell>
                    <TableCell>{subject.name}</TableCell>
                    <TableCell>{subject.courseName}</TableCell>
                    <TableCell>{subject.semester}</TableCell>
                    <TableCell>{subject.credits}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon-sm" onClick={() => openEdit(subject)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(subject);
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
              <DialogTitle>{editing ? "Edit subject" : "Add subject"}</DialogTitle>
              <DialogDescription>
                {editing ? `Update ${editing.name}.` : "Create a new subject under a course."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="subject-code">Code</Label>
                <Input
                  id="subject-code"
                  value={form.code}
                  maxLength={20}
                  onChange={(event) => setForm((f) => ({ ...f, code: event.target.value }))}
                  aria-invalid={!!fieldErrors.code}
                  placeholder="CS301"
                />
                {fieldErrors.code && <p className="text-xs text-destructive">{fieldErrors.code}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="subject-name">Name</Label>
                <Input
                  id="subject-name"
                  value={form.name}
                  maxLength={150}
                  onChange={(event) => setForm((f) => ({ ...f, name: event.target.value }))}
                  aria-invalid={!!fieldErrors.name}
                  placeholder="Data Structures"
                />
                {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="subject-course">Course</Label>
                <Select
                  value={form.courseId}
                  onValueChange={(value) => setForm((f) => ({ ...f, courseId: value ?? "" }))}
                >
                  <SelectTrigger id="subject-course" className="w-full" aria-invalid={!!fieldErrors.courseId}>
                    <SelectValue placeholder="Select a course" />
                  </SelectTrigger>
                  <SelectContent>
                    {courses.map((course) => (
                      <SelectItem key={course.id} value={String(course.id)}>
                        {course.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {fieldErrors.courseId && (
                  <p className="text-xs text-destructive">{fieldErrors.courseId}</p>
                )}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="subject-semester">Semester</Label>
                  <Input
                    id="subject-semester"
                    type="number"
                    min={1}
                    value={form.semester}
                    onChange={(event) => setForm((f) => ({ ...f, semester: event.target.value }))}
                    aria-invalid={!!fieldErrors.semester}
                  />
                  {fieldErrors.semester && (
                    <p className="text-xs text-destructive">{fieldErrors.semester}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="subject-credits">Credits</Label>
                  <Input
                    id="subject-credits"
                    type="number"
                    min={1}
                    max={10}
                    value={form.credits}
                    onChange={(event) => setForm((f) => ({ ...f, credits: event.target.value }))}
                    aria-invalid={!!fieldErrors.credits}
                  />
                  {fieldErrors.credits && (
                    <p className="text-xs text-destructive">{fieldErrors.credits}</p>
                  )}
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create subject"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete subject?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes <strong>{deleteTarget?.name}</strong>. Subjects with
                enrollments or faculty assignments cannot be deleted.
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
