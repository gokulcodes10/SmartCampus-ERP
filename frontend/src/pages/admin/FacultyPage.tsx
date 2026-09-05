import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, PowerIcon, PowerOffIcon } from "lucide-react";

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
import * as departmentService from "@/services/departmentService";
import * as facultyService from "@/services/facultyService";
import { FacultyCreationError } from "@/services/facultyService";
import type {
  DepartmentResponse,
  FacultyResponse,
  FacultyStatus,
  FacultyUpdateRequest,
} from "@/types/academic";
import { MIN_PASSWORD_LENGTH, isValidEmail, isValidPassword } from "@/utils/validation";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const CREATE_FIELDS = ["email", "password", "fullName", "employeeCode", "departmentId", "designation"] as const;
const EDIT_FIELDS = ["employeeCode", "departmentId", "designation"] as const;
const ALL_DEPARTMENTS = "all";
const ALL_STATUSES = "all";

interface CreateFormState {
  email: string;
  password: string;
  fullName: string;
  employeeCode: string;
  departmentId: string;
  designation: string;
}

const EMPTY_CREATE_FORM: CreateFormState = {
  email: "",
  password: "",
  fullName: "",
  employeeCode: "",
  departmentId: "",
  designation: "",
};

interface EditFormState {
  employeeCode: string;
  departmentId: string;
  designation: string;
}

function statusBadgeVariant(status: FacultyStatus) {
  return status === "ACTIVE" ? "default" : "secondary";
}

export default function FacultyPage() {
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
      ...(filterStatus === ALL_STATUSES ? {} : { status: filterStatus as FacultyStatus }),
    }),
    [filterDepartmentId, filterStatus],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    facultyService.listFaculty,
    filters,
    { sort: "user.fullName,asc" },
  );

  // Create (two-step: provision account, then create faculty profile)
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateFormState>(EMPTY_CREATE_FORM);
  const [createFieldErrors, setCreateFieldErrors] = useState<Record<string, string>>({});
  const [createFormError, setCreateFormError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  // Edit
  const [editing, setEditing] = useState<FacultyResponse | null>(null);
  const [editForm, setEditForm] = useState<EditFormState>({ employeeCode: "", departmentId: "", designation: "" });
  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>({});
  const [editFormError, setEditFormError] = useState<string | null>(null);
  const [isSavingEdit, setIsSavingEdit] = useState(false);

  // Status toggle
  const [statusTarget, setStatusTarget] = useState<FacultyResponse | null>(null);
  const [isTogglingStatus, setIsTogglingStatus] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);

  function openCreate() {
    setCreateForm(EMPTY_CREATE_FORM);
    setCreateFieldErrors({});
    setCreateFormError(null);
    setCreateOpen(true);
  }

  function validateCreate(): boolean {
    const errors: Record<string, string> = {};
    if (!createForm.fullName.trim()) errors.fullName = "Full name is required.";
    if (!createForm.email.trim()) errors.email = "Email is required.";
    else if (!isValidEmail(createForm.email)) errors.email = "Enter a valid email address.";
    if (!createForm.password) errors.password = "Password is required.";
    else if (!isValidPassword(createForm.password))
      errors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
    if (!createForm.employeeCode.trim()) errors.employeeCode = "Employee code is required.";
    if (!createForm.departmentId) errors.departmentId = "Department is required.";
    setCreateFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setCreateFormError(null);
    if (!validateCreate()) return;

    setIsCreating(true);
    try {
      await facultyService.createFaculty({
        email: createForm.email.trim(),
        password: createForm.password,
        fullName: createForm.fullName.trim(),
        employeeCode: createForm.employeeCode.trim(),
        departmentId: Number(createForm.departmentId),
        designation: createForm.designation.trim() || undefined,
      });
      setCreateOpen(false);
      refresh();
    } catch (err) {
      if (err instanceof FacultyCreationError) {
        const raw = extractRawErrorMessage(err.cause);
        if (raw) {
          const parsed = parseFieldErrors(raw, CREATE_FIELDS);
          setCreateFieldErrors(parsed.fieldErrors);
          setCreateFormError(parsed.formError ?? err.message);
        } else {
          setCreateFormError(extractErrorMessage(err.cause, err.message));
        }
      } else {
        setCreateFormError(extractErrorMessage(err, "Failed to create the faculty account."));
      }
    } finally {
      setIsCreating(false);
    }
  }

  function openEdit(faculty: FacultyResponse) {
    setEditing(faculty);
    setEditForm({
      employeeCode: faculty.employeeCode,
      departmentId: String(faculty.departmentId),
      designation: faculty.designation ?? "",
    });
    setEditFieldErrors({});
    setEditFormError(null);
  }

  function validateEdit(): boolean {
    const errors: Record<string, string> = {};
    if (!editForm.employeeCode.trim()) errors.employeeCode = "Employee code is required.";
    if (!editForm.departmentId) errors.departmentId = "Department is required.";
    setEditFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleEditSubmit(event: FormEvent) {
    event.preventDefault();
    if (!editing) return;
    setEditFormError(null);
    if (!validateEdit()) return;

    const payload: FacultyUpdateRequest = {
      employeeCode: editForm.employeeCode.trim(),
      departmentId: Number(editForm.departmentId),
      designation: editForm.designation.trim() || undefined,
      status: editing.status,
    };
    setIsSavingEdit(true);
    try {
      await facultyService.updateFaculty(editing.id, payload);
      setEditing(null);
      refresh();
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, EDIT_FIELDS);
        setEditFieldErrors(parsed.fieldErrors);
        setEditFormError(parsed.formError);
      } else {
        setEditFormError(extractErrorMessage(err, "Failed to save faculty details."));
      }
    } finally {
      setIsSavingEdit(false);
    }
  }

  async function handleToggleStatus() {
    if (!statusTarget) return;
    const nextStatus: FacultyStatus = statusTarget.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setIsTogglingStatus(true);
    setStatusError(null);
    try {
      await facultyService.updateFaculty(statusTarget.id, {
        employeeCode: statusTarget.employeeCode,
        departmentId: statusTarget.departmentId,
        designation: statusTarget.designation ?? undefined,
        status: nextStatus,
      });
      setStatusTarget(null);
      refresh();
    } catch (err) {
      setStatusError(extractErrorMessage(err, "Failed to update faculty status."));
    } finally {
      setIsTogglingStatus(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Faculty</h1>
          <p className="text-muted-foreground">
            Admin-provisioned staff accounts and their department assignment.
          </p>
        </div>
        <Button onClick={openCreate} disabled={departments.length === 0}>
          <PlusIcon />
          Add faculty
        </Button>
      </div>

      {departmentsError && (
        <Alert variant="destructive">
          <AlertDescription>{departmentsError}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>All faculty</CardTitle>
          <CardDescription>Search by name, email or employee code.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search faculty…"
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
            <Select value={filterStatus} onValueChange={(value) => setFilterStatus(value ?? ALL_STATUSES)}>
              <SelectTrigger className="w-40">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
                <SelectItem value="ACTIVE">Active</SelectItem>
                <SelectItem value="INACTIVE">Inactive</SelectItem>
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
                <TableHead>Employee code</TableHead>
                <TableHead>Department</TableHead>
                <TableHead>Designation</TableHead>
                <TableHead>Status</TableHead>
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
                    No faculty found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((faculty) => (
                  <TableRow key={faculty.id}>
                    <TableCell className="font-medium">{faculty.fullName}</TableCell>
                    <TableCell>{faculty.employeeCode}</TableCell>
                    <TableCell>{faculty.departmentName}</TableCell>
                    <TableCell>{faculty.designation ?? "—"}</TableCell>
                    <TableCell>
                      <Badge variant={statusBadgeVariant(faculty.status)}>{faculty.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(faculty)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setStatusError(null);
                            setStatusTarget(faculty);
                          }}
                        >
                          {faculty.status === "ACTIVE" ? <PowerOffIcon /> : <PowerIcon />}
                          <span className="sr-only">
                            {faculty.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
                          </span>
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

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <form onSubmit={handleCreate}>
            <DialogHeader>
              <DialogTitle>Add faculty</DialogTitle>
              <DialogDescription>
                Creates a login account and a faculty profile together.
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {createFormError && (
                <Alert variant="destructive">
                  <AlertDescription>{createFormError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="faculty-fullname">Full name</Label>
                <Input
                  id="faculty-fullname"
                  value={createForm.fullName}
                  onChange={(event) => setCreateForm((f) => ({ ...f, fullName: event.target.value }))}
                  aria-invalid={!!createFieldErrors.fullName}
                />
                {createFieldErrors.fullName && (
                  <p className="text-xs text-destructive">{createFieldErrors.fullName}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="faculty-email">Email</Label>
                <Input
                  id="faculty-email"
                  type="email"
                  value={createForm.email}
                  onChange={(event) => setCreateForm((f) => ({ ...f, email: event.target.value }))}
                  aria-invalid={!!createFieldErrors.email}
                />
                {createFieldErrors.email && (
                  <p className="text-xs text-destructive">{createFieldErrors.email}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="faculty-password">Temporary password</Label>
                <Input
                  id="faculty-password"
                  type="password"
                  value={createForm.password}
                  onChange={(event) => setCreateForm((f) => ({ ...f, password: event.target.value }))}
                  aria-invalid={!!createFieldErrors.password}
                />
                {createFieldErrors.password && (
                  <p className="text-xs text-destructive">{createFieldErrors.password}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="faculty-code">Employee code</Label>
                <Input
                  id="faculty-code"
                  value={createForm.employeeCode}
                  maxLength={20}
                  onChange={(event) => setCreateForm((f) => ({ ...f, employeeCode: event.target.value }))}
                  aria-invalid={!!createFieldErrors.employeeCode}
                  placeholder="EMP-1042"
                />
                {createFieldErrors.employeeCode && (
                  <p className="text-xs text-destructive">{createFieldErrors.employeeCode}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="faculty-department">Department</Label>
                <Select
                  value={createForm.departmentId}
                  onValueChange={(value) => setCreateForm((f) => ({ ...f, departmentId: value ?? "" }))}
                >
                  <SelectTrigger
                    id="faculty-department"
                    className="w-full"
                    aria-invalid={!!createFieldErrors.departmentId}
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
                {createFieldErrors.departmentId && (
                  <p className="text-xs text-destructive">{createFieldErrors.departmentId}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="faculty-designation">Designation (optional)</Label>
                <Input
                  id="faculty-designation"
                  value={createForm.designation}
                  maxLength={100}
                  onChange={(event) => setCreateForm((f) => ({ ...f, designation: event.target.value }))}
                  placeholder="Assistant Professor"
                />
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isCreating}>
                {isCreating ? "Creating…" : "Create faculty"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent>
          <form onSubmit={handleEditSubmit}>
            <DialogHeader>
              <DialogTitle>Edit faculty</DialogTitle>
              <DialogDescription>
                {editing ? `Update ${editing.fullName}.` : ""}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {editFormError && (
                <Alert variant="destructive">
                  <AlertDescription>{editFormError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="faculty-edit-code">Employee code</Label>
                <Input
                  id="faculty-edit-code"
                  value={editForm.employeeCode}
                  maxLength={20}
                  onChange={(event) => setEditForm((f) => ({ ...f, employeeCode: event.target.value }))}
                  aria-invalid={!!editFieldErrors.employeeCode}
                />
                {editFieldErrors.employeeCode && (
                  <p className="text-xs text-destructive">{editFieldErrors.employeeCode}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="faculty-edit-department">Department</Label>
                <Select
                  value={editForm.departmentId}
                  onValueChange={(value) => setEditForm((f) => ({ ...f, departmentId: value ?? "" }))}
                >
                  <SelectTrigger
                    id="faculty-edit-department"
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
                <Label htmlFor="faculty-edit-designation">Designation</Label>
                <Input
                  id="faculty-edit-designation"
                  value={editForm.designation}
                  maxLength={100}
                  onChange={(event) => setEditForm((f) => ({ ...f, designation: event.target.value }))}
                />
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
        title={statusTarget?.status === "ACTIVE" ? "Deactivate faculty?" : "Reactivate faculty?"}
        description={
          <>
            {statusError ? (
              <span className="text-destructive">{statusError}</span>
            ) : statusTarget?.status === "ACTIVE" ? (
              <>
                <strong>{statusTarget?.fullName}</strong> will no longer be able to access
                faculty features. This does not delete their record and can be reversed.
              </>
            ) : (
              <>
                <strong>{statusTarget?.fullName}</strong> will regain access to faculty
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
