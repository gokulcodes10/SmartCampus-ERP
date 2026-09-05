import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { PencilIcon, PlusIcon, Trash2Icon, UsersIcon } from "lucide-react";

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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as companyService from "@/services/companyService";
import * as departmentService from "@/services/departmentService";
import * as jobService from "@/services/jobService";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";
import type { DepartmentResponse } from "@/types/academic";
import type {
  CompanyResponse,
  JobCreateRequest,
  JobResponse,
  JobStatus,
  JobType,
  JobUpdateRequest,
} from "@/types/placement";

const ALL_VALUE = "all";
const JOB_TYPES: JobType[] = ["FULL_TIME", "PART_TIME", "INTERNSHIP", "CONTRACT"];
const JOB_STATUSES: JobStatus[] = ["DRAFT", "OPEN", "CLOSED", "CANCELLED"];
const CREATE_STATUSES: JobStatus[] = ["DRAFT", "OPEN"];

const JOB_TYPE_LABELS: Record<JobType, string> = {
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  INTERNSHIP: "Internship",
  CONTRACT: "Contract",
};

const STATUS_BADGE: Record<JobStatus, string> = {
  DRAFT: "border-border bg-muted text-muted-foreground",
  OPEN: "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400",
  CLOSED: "border-border bg-muted text-muted-foreground",
  CANCELLED: "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-400",
};

const FORM_FIELDS = [
  "companyId",
  "title",
  "description",
  "location",
  "jobType",
  "openings",
  "salaryMin",
  "salaryMax",
  "salaryCurrency",
  "minCgpa",
  "minMarksPercentage",
  "graduationYear",
  "applicationDeadline",
  "driveDate",
] as const;

interface FormState {
  companyId: string;
  title: string;
  description: string;
  location: string;
  jobType: JobType;
  openings: string;
  salaryMin: string;
  salaryMax: string;
  salaryCurrency: string;
  minCgpa: string;
  minMarksPercentage: string;
  graduationYear: string;
  eligibleDepartmentIds: number[];
  applicationDeadline: string;
  driveDate: string;
  status: JobStatus;
}

const EMPTY_FORM: FormState = {
  companyId: "",
  title: "",
  description: "",
  location: "",
  jobType: "FULL_TIME",
  openings: "",
  salaryMin: "",
  salaryMax: "",
  salaryCurrency: "INR",
  minCgpa: "",
  minMarksPercentage: "",
  graduationYear: "",
  eligibleDepartmentIds: [],
  applicationDeadline: "",
  driveDate: "",
  status: "DRAFT",
};

function orNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

function numOrNull(value: string): number | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : Number(trimmed);
}

/**
 * `/admin/jobs` — CRUD for placement drives, including eligibility criteria and the
 * eligible-department multi-select. Status is changed only via the dedicated
 * PATCH /{id}/status control, never through the PUT form (JobUpdateRequest has no
 * status field — see types/placement.ts).
 */
export default function JobsPage() {
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [lookupError, setLookupError] = useState<string | null>(null);

  useEffect(() => {
    companyService
      .listAllCompanies()
      .then(setCompanies)
      .catch((err) => setLookupError(extractErrorMessage(err, "Failed to load companies.")));
    departmentService
      .listAllDepartments()
      .then(setDepartments)
      .catch((err) => setLookupError((prev) => prev ?? extractErrorMessage(err, "Failed to load departments.")));
  }, []);

  const [companyFilter, setCompanyFilter] = useState<string>(ALL_VALUE);
  const [jobTypeFilter, setJobTypeFilter] = useState<string>(ALL_VALUE);
  const [statusFilter, setStatusFilter] = useState<string>(ALL_VALUE);
  const [departmentFilter, setDepartmentFilter] = useState<string>(ALL_VALUE);

  const filters = useMemo(() => {
    const f: {
      companyId?: number;
      jobType?: JobType;
      status?: JobStatus;
      departmentId?: number;
    } = {};
    if (companyFilter !== ALL_VALUE) f.companyId = Number(companyFilter);
    if (jobTypeFilter !== ALL_VALUE) f.jobType = jobTypeFilter as JobType;
    if (statusFilter !== ALL_VALUE) f.status = statusFilter as JobStatus;
    if (departmentFilter !== ALL_VALUE) f.departmentId = Number(departmentFilter);
    return f;
  }, [companyFilter, jobTypeFilter, statusFilter, departmentFilter]);

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    jobService.listJobs,
    filters,
    { sort: "applicationDeadline,desc" },
  );

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<JobResponse | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<JobResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const [statusUpdatingId, setStatusUpdatingId] = useState<number | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(job: JobResponse) {
    setEditing(job);
    setForm({
      companyId: String(job.companyId),
      title: job.title,
      description: job.description ?? "",
      location: job.location ?? "",
      jobType: job.jobType,
      openings: job.openings === null ? "" : String(job.openings),
      salaryMin: job.salaryMin === null ? "" : String(job.salaryMin),
      salaryMax: job.salaryMax === null ? "" : String(job.salaryMax),
      salaryCurrency: job.salaryCurrency,
      minCgpa: job.minCgpa === null ? "" : String(job.minCgpa),
      minMarksPercentage: job.minMarksPercentage === null ? "" : String(job.minMarksPercentage),
      graduationYear: job.graduationYear === null ? "" : String(job.graduationYear),
      eligibleDepartmentIds: job.eligibleDepartments.map((d) => d.id),
      applicationDeadline: job.applicationDeadline.slice(0, 16),
      driveDate: job.driveDate ?? "",
      status: job.status,
    });
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function toggleDepartment(id: number) {
    setForm((f) => ({
      ...f,
      eligibleDepartmentIds: f.eligibleDepartmentIds.includes(id)
        ? f.eligibleDepartmentIds.filter((d) => d !== id)
        : [...f.eligibleDepartmentIds, id],
    }));
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!editing && !form.companyId) errors.companyId = "Company is required.";
    if (!form.title.trim()) errors.title = "Title is required.";
    if (!form.applicationDeadline) errors.applicationDeadline = "Application deadline is required.";
    if (form.salaryCurrency.trim() && !/^[A-Z]{3}$/.test(form.salaryCurrency.trim())) {
      errors.salaryCurrency = "Use a 3-letter currency code, e.g. INR.";
    }
    if (form.salaryMin && form.salaryMax && Number(form.salaryMin) > Number(form.salaryMax)) {
      errors.salaryMax = "Maximum salary must be at least the minimum.";
    }
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    setIsSaving(true);
    try {
      if (editing) {
        const payload: JobUpdateRequest = {
          title: form.title.trim(),
          description: orNull(form.description),
          location: orNull(form.location),
          jobType: form.jobType,
          openings: numOrNull(form.openings),
          salaryMin: numOrNull(form.salaryMin),
          salaryMax: numOrNull(form.salaryMax),
          salaryCurrency: orNull(form.salaryCurrency),
          minCgpa: numOrNull(form.minCgpa),
          minMarksPercentage: numOrNull(form.minMarksPercentage),
          graduationYear: numOrNull(form.graduationYear),
          eligibleDepartmentIds: form.eligibleDepartmentIds,
          applicationDeadline: form.applicationDeadline,
          driveDate: orNull(form.driveDate),
        };
        await jobService.updateJob(editing.id, payload);
      } else {
        const payload: JobCreateRequest = {
          companyId: Number(form.companyId),
          title: form.title.trim(),
          description: orNull(form.description),
          location: orNull(form.location),
          jobType: form.jobType,
          openings: numOrNull(form.openings),
          salaryMin: numOrNull(form.salaryMin),
          salaryMax: numOrNull(form.salaryMax),
          salaryCurrency: orNull(form.salaryCurrency),
          minCgpa: numOrNull(form.minCgpa),
          minMarksPercentage: numOrNull(form.minMarksPercentage),
          graduationYear: numOrNull(form.graduationYear),
          eligibleDepartmentIds: form.eligibleDepartmentIds,
          applicationDeadline: form.applicationDeadline,
          driveDate: orNull(form.driveDate),
          status: form.status,
        };
        await jobService.createJob(payload);
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
        setFormError(extractErrorMessage(err, "Failed to save this job."));
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
      await jobService.deleteJob(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(
        extractErrorMessage(err, "Failed to delete this job. It may already have applications."),
      );
    } finally {
      setIsDeleting(false);
    }
  }

  async function handleStatusChange(job: JobResponse, status: JobStatus) {
    if (status === job.status) return;
    setStatusUpdatingId(job.id);
    setStatusError(null);
    try {
      await jobService.updateJobStatus(job.id, { status });
      refresh();
    } catch (err) {
      setStatusError(extractErrorMessage(err, `Failed to change status for "${job.title}".`));
    } finally {
      setStatusUpdatingId(null);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Placement drives</h1>
          <p className="text-muted-foreground">Job postings from recruiting companies.</p>
        </div>
        <Button onClick={openCreate} disabled={companies.length === 0}>
          <PlusIcon />
          Add drive
        </Button>
      </div>

      {lookupError && (
        <Alert variant="destructive">
          <AlertDescription>{lookupError}</AlertDescription>
        </Alert>
      )}
      {statusError && (
        <Alert variant="destructive">
          <AlertDescription>{statusError}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>All drives</CardTitle>
          <CardDescription>Search by title, or filter by company, type, status and department.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search drives…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Select value={companyFilter} onValueChange={(value) => setCompanyFilter(value ?? ALL_VALUE)}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="All companies" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>All companies</SelectItem>
                {companies.map((c) => (
                  <SelectItem key={c.id} value={String(c.id)}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={jobTypeFilter} onValueChange={(value) => setJobTypeFilter(value ?? ALL_VALUE)}>
              <SelectTrigger className="w-40">
                <SelectValue placeholder="All types" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>All types</SelectItem>
                {JOB_TYPES.map((t) => (
                  <SelectItem key={t} value={t}>
                    {JOB_TYPE_LABELS[t]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value ?? ALL_VALUE)}>
              <SelectTrigger className="w-36">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>All statuses</SelectItem>
                {JOB_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s.charAt(0) + s.slice(1).toLowerCase()}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={departmentFilter} onValueChange={(value) => setDepartmentFilter(value ?? ALL_VALUE)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All departments" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_VALUE}>All departments</SelectItem>
                {departments.map((d) => (
                  <SelectItem key={d.id} value={String(d.id)}>
                    {d.name}
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
                <TableHead>Company</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Deadline</TableHead>
                <TableHead>Applications</TableHead>
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
                    No drives found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((job) => (
                  <TableRow key={job.id}>
                    <TableCell className="font-medium">{job.title}</TableCell>
                    <TableCell>{job.companyName}</TableCell>
                    <TableCell>{JOB_TYPE_LABELS[job.jobType]}</TableCell>
                    <TableCell>
                      <Select
                        value={job.status}
                        onValueChange={(value) => value && handleStatusChange(job, value as JobStatus)}
                      >
                        <SelectTrigger
                          size="sm"
                          className={STATUS_BADGE[job.status]}
                          disabled={statusUpdatingId === job.id}
                        >
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {JOB_STATUSES.map((s) => (
                            <SelectItem key={s} value={s}>
                              {s.charAt(0) + s.slice(1).toLowerCase()}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(job.applicationDeadline).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Link
                        to={`/admin/jobs/${job.id}/applicants`}
                        className="flex items-center gap-1 hover:underline"
                      >
                        <UsersIcon className="size-3.5" />
                        {job.applicationCount}
                      </Link>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(job)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(job);
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
        <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit drive" : "Add drive"}</DialogTitle>
              <DialogDescription>
                {editing
                  ? `Update ${editing.title}. Status is changed from the table, not here.`
                  : "Create a new placement drive."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="job-company">Company</Label>
                  {editing ? (
                    <Input id="job-company" value={editing.companyName} disabled readOnly />
                  ) : (
                    <Select
                      value={form.companyId}
                      onValueChange={(value) => setForm((f) => ({ ...f, companyId: value ?? "" }))}
                    >
                      <SelectTrigger id="job-company" className="w-full" aria-invalid={!!fieldErrors.companyId}>
                        <SelectValue placeholder="Select a company" />
                      </SelectTrigger>
                      <SelectContent>
                        {companies.map((c) => (
                          <SelectItem key={c.id} value={String(c.id)}>
                            {c.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                  {editing && (
                    <p className="text-xs text-muted-foreground">The company of a drive cannot be changed.</p>
                  )}
                  {fieldErrors.companyId && <p className="text-xs text-destructive">{fieldErrors.companyId}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-title">Title</Label>
                  <Input
                    id="job-title"
                    value={form.title}
                    maxLength={150}
                    onChange={(event) => setForm((f) => ({ ...f, title: event.target.value }))}
                    aria-invalid={!!fieldErrors.title}
                    placeholder="Software Engineer"
                  />
                  {fieldErrors.title && <p className="text-xs text-destructive">{fieldErrors.title}</p>}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="job-description">Description</Label>
                <textarea
                  id="job-description"
                  className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.description}
                  onChange={(event) => setForm((f) => ({ ...f, description: event.target.value }))}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label htmlFor="job-location">Location</Label>
                  <Input
                    id="job-location"
                    value={form.location}
                    maxLength={150}
                    onChange={(event) => setForm((f) => ({ ...f, location: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-type">Job type</Label>
                  <Select
                    value={form.jobType}
                    onValueChange={(value) => value && setForm((f) => ({ ...f, jobType: value as JobType }))}
                  >
                    <SelectTrigger id="job-type" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {JOB_TYPES.map((t) => (
                        <SelectItem key={t} value={t}>
                          {JOB_TYPE_LABELS[t]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-openings">Openings</Label>
                  <Input
                    id="job-openings"
                    type="number"
                    min={1}
                    value={form.openings}
                    onChange={(event) => setForm((f) => ({ ...f, openings: event.target.value }))}
                  />
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label htmlFor="job-salary-min">Salary min</Label>
                  <Input
                    id="job-salary-min"
                    type="number"
                    min={0}
                    step="0.01"
                    value={form.salaryMin}
                    onChange={(event) => setForm((f) => ({ ...f, salaryMin: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-salary-max">Salary max</Label>
                  <Input
                    id="job-salary-max"
                    type="number"
                    min={0}
                    step="0.01"
                    value={form.salaryMax}
                    onChange={(event) => setForm((f) => ({ ...f, salaryMax: event.target.value }))}
                    aria-invalid={!!fieldErrors.salaryMax}
                  />
                  {fieldErrors.salaryMax && <p className="text-xs text-destructive">{fieldErrors.salaryMax}</p>}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-salary-currency">Currency</Label>
                  <Input
                    id="job-salary-currency"
                    value={form.salaryCurrency}
                    maxLength={3}
                    onChange={(event) =>
                      setForm((f) => ({ ...f, salaryCurrency: event.target.value.toUpperCase() }))
                    }
                    aria-invalid={!!fieldErrors.salaryCurrency}
                    placeholder="INR"
                  />
                  {fieldErrors.salaryCurrency && (
                    <p className="text-xs text-destructive">{fieldErrors.salaryCurrency}</p>
                  )}
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label htmlFor="job-min-cgpa">Minimum CGPA</Label>
                  <Input
                    id="job-min-cgpa"
                    type="number"
                    min={0}
                    max={10}
                    step="0.01"
                    value={form.minCgpa}
                    onChange={(event) => setForm((f) => ({ ...f, minCgpa: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-min-marks">Minimum aggregate %</Label>
                  <Input
                    id="job-min-marks"
                    type="number"
                    min={0}
                    max={100}
                    step="0.01"
                    value={form.minMarksPercentage}
                    onChange={(event) => setForm((f) => ({ ...f, minMarksPercentage: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-grad-year">Graduation year</Label>
                  <Input
                    id="job-grad-year"
                    type="number"
                    min={1950}
                    max={2100}
                    value={form.graduationYear}
                    onChange={(event) => setForm((f) => ({ ...f, graduationYear: event.target.value }))}
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label>Eligible departments</Label>
                <p className="text-xs text-muted-foreground">
                  Leave every department unchecked to open this drive to <strong>all</strong> departments —
                  that is a real, meaningful choice here, not an unfilled field.
                </p>
                <div className="max-h-40 space-y-1 overflow-y-auto rounded-lg border border-border p-2">
                  {departments.map((d) => (
                    <label key={d.id} className="flex items-center gap-2 text-sm">
                      <input
                        type="checkbox"
                        className="size-4 rounded border-border"
                        checked={form.eligibleDepartmentIds.includes(d.id)}
                        onChange={() => toggleDepartment(d.id)}
                      />
                      {d.name}
                    </label>
                  ))}
                </div>
                <p className="text-xs font-medium">
                  {form.eligibleDepartmentIds.length === 0
                    ? "Open to all departments."
                    : `Open only to ${form.eligibleDepartmentIds.length} selected department(s).`}
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="job-deadline">Application deadline</Label>
                  <Input
                    id="job-deadline"
                    type="datetime-local"
                    value={form.applicationDeadline}
                    onChange={(event) => setForm((f) => ({ ...f, applicationDeadline: event.target.value }))}
                    aria-invalid={!!fieldErrors.applicationDeadline}
                  />
                  {fieldErrors.applicationDeadline && (
                    <p className="text-xs text-destructive">{fieldErrors.applicationDeadline}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="job-drive-date">Drive date</Label>
                  <Input
                    id="job-drive-date"
                    type="date"
                    value={form.driveDate}
                    onChange={(event) => setForm((f) => ({ ...f, driveDate: event.target.value }))}
                  />
                </div>
              </div>

              {!editing && (
                <div className="space-y-1.5">
                  <Label htmlFor="job-status">Initial status</Label>
                  <Select
                    value={form.status}
                    onValueChange={(value) => value && setForm((f) => ({ ...f, status: value as JobStatus }))}
                  >
                    <SelectTrigger id="job-status" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {CREATE_STATUSES.map((s) => (
                        <SelectItem key={s} value={s}>
                          {s.charAt(0) + s.slice(1).toLowerCase()}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create drive"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete drive?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes <strong>{deleteTarget?.title}</strong>. Drives with existing
                applications cannot be deleted.
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
