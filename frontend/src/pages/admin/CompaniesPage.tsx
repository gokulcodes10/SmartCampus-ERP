import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";

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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useServerTable } from "@/hooks/useServerTable";
import * as companyService from "@/services/companyService";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";
import type { CompanyCreateRequest, CompanyResponse, CompanyStatus, CompanyUpdateRequest } from "@/types/placement";

const ALL_STATUSES = "all";
const STATUSES: CompanyStatus[] = ["ACTIVE", "INACTIVE"];

const FORM_FIELDS = [
  "name",
  "industry",
  "website",
  "description",
  "location",
  "contactPerson",
  "contactEmail",
  "contactPhone",
  "status",
] as const;

interface FormState {
  name: string;
  industry: string;
  website: string;
  description: string;
  location: string;
  contactPerson: string;
  contactEmail: string;
  contactPhone: string;
  status: CompanyStatus;
}

const EMPTY_FORM: FormState = {
  name: "",
  industry: "",
  website: "",
  description: "",
  location: "",
  contactPerson: "",
  contactEmail: "",
  contactPhone: "",
  status: "ACTIVE",
};

function orNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === "" ? null : trimmed;
}

/** `/admin/companies` — CRUD for recruiting companies. Mirrors SubjectsPage's shape. */
export default function CompaniesPage() {
  const [statusFilter, setStatusFilter] = useState<string>(ALL_STATUSES);
  const [industryFilter, setIndustryFilter] = useState("");

  const filters = useMemo(() => {
    const f: { status?: CompanyStatus; industry?: string } = {};
    if (statusFilter !== ALL_STATUSES) f.status = statusFilter as CompanyStatus;
    if (industryFilter.trim()) f.industry = industryFilter.trim();
    return f;
  }, [statusFilter, industryFilter]);

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable(
    companyService.listCompanies,
    filters,
    { sort: "name,asc" },
  );

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<CompanyResponse | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<CompanyResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(company: CompanyResponse) {
    setEditing(company);
    setForm({
      name: company.name,
      industry: company.industry ?? "",
      website: company.website ?? "",
      description: company.description ?? "",
      location: company.location ?? "",
      contactPerson: company.contactPerson ?? "",
      contactEmail: company.contactEmail ?? "",
      contactPhone: company.contactPhone ?? "",
      status: company.status,
    });
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = "Name is required.";
    if (form.contactEmail.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.contactEmail.trim())) {
      errors.contactEmail = "Enter a valid email address.";
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
        const payload: CompanyUpdateRequest = {
          name: form.name.trim(),
          industry: orNull(form.industry),
          website: orNull(form.website),
          description: orNull(form.description),
          location: orNull(form.location),
          contactPerson: orNull(form.contactPerson),
          contactEmail: orNull(form.contactEmail),
          contactPhone: orNull(form.contactPhone),
          status: form.status,
        };
        await companyService.updateCompany(editing.id, payload);
      } else {
        const payload: CompanyCreateRequest = {
          name: form.name.trim(),
          industry: orNull(form.industry),
          website: orNull(form.website),
          description: orNull(form.description),
          location: orNull(form.location),
          contactPerson: orNull(form.contactPerson),
          contactEmail: orNull(form.contactEmail),
          contactPhone: orNull(form.contactPhone),
        };
        await companyService.createCompany(payload);
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
        setFormError(extractErrorMessage(err, "Failed to save this company."));
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
      await companyService.deleteCompany(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(
        extractErrorMessage(err, "Failed to delete this company. It may still have job drives."),
      );
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Companies</h1>
          <p className="text-muted-foreground">Recruiting companies that post placement drives.</p>
        </div>
        <Button onClick={openCreate}>
          <PlusIcon />
          Add company
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All companies</CardTitle>
          <CardDescription>Search by name, or filter by status and industry.</CardDescription>
          <div className="flex flex-wrap gap-2 pt-2">
            <Input
              placeholder="Search companies…"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="max-w-sm"
            />
            <Input
              placeholder="Industry…"
              value={industryFilter}
              onChange={(event) => setIndustryFilter(event.target.value)}
              className="max-w-48"
            />
            <Select value={statusFilter} onValueChange={(value) => setStatusFilter(value ?? ALL_STATUSES)}>
              <SelectTrigger className="w-40">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_STATUSES}>All statuses</SelectItem>
                {STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s.charAt(0) + s.slice(1).toLowerCase()}
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
                <TableHead>Industry</TableHead>
                <TableHead>Location</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Jobs (open)</TableHead>
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
                    No companies found.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((company) => (
                  <TableRow key={company.id}>
                    <TableCell className="font-medium">{company.name}</TableCell>
                    <TableCell className="text-muted-foreground">{company.industry ?? "—"}</TableCell>
                    <TableCell className="text-muted-foreground">{company.location ?? "—"}</TableCell>
                    <TableCell>
                      <Badge variant={company.status === "ACTIVE" ? "default" : "outline"}>
                        {company.status === "ACTIVE" ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {company.jobCount} ({company.openJobCount} open)
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(company)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(company);
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
        <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit company" : "Add company"}</DialogTitle>
              <DialogDescription>
                {editing ? `Update ${editing.name}.` : "Register a new recruiting company."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="company-name">Name</Label>
                <Input
                  id="company-name"
                  value={form.name}
                  maxLength={150}
                  onChange={(event) => setForm((f) => ({ ...f, name: event.target.value }))}
                  aria-invalid={!!fieldErrors.name}
                  placeholder="Acme Corp"
                />
                {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="company-industry">Industry</Label>
                  <Input
                    id="company-industry"
                    value={form.industry}
                    maxLength={100}
                    onChange={(event) => setForm((f) => ({ ...f, industry: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="company-website">Website</Label>
                  <Input
                    id="company-website"
                    value={form.website}
                    maxLength={255}
                    onChange={(event) => setForm((f) => ({ ...f, website: event.target.value }))}
                    placeholder="https://example.com"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="company-description">Description</Label>
                <textarea
                  id="company-description"
                  className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.description}
                  onChange={(event) => setForm((f) => ({ ...f, description: event.target.value }))}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="company-location">Location</Label>
                <Input
                  id="company-location"
                  value={form.location}
                  maxLength={150}
                  onChange={(event) => setForm((f) => ({ ...f, location: event.target.value }))}
                />
              </div>

              <div className="grid gap-4 sm:grid-cols-3">
                <div className="space-y-1.5">
                  <Label htmlFor="company-contact-person">Contact person</Label>
                  <Input
                    id="company-contact-person"
                    value={form.contactPerson}
                    maxLength={120}
                    onChange={(event) => setForm((f) => ({ ...f, contactPerson: event.target.value }))}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="company-contact-email">Contact email</Label>
                  <Input
                    id="company-contact-email"
                    type="email"
                    value={form.contactEmail}
                    maxLength={255}
                    onChange={(event) => setForm((f) => ({ ...f, contactEmail: event.target.value }))}
                    aria-invalid={!!fieldErrors.contactEmail}
                  />
                  {fieldErrors.contactEmail && (
                    <p className="text-xs text-destructive">{fieldErrors.contactEmail}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="company-contact-phone">Contact phone</Label>
                  <Input
                    id="company-contact-phone"
                    value={form.contactPhone}
                    maxLength={20}
                    onChange={(event) => setForm((f) => ({ ...f, contactPhone: event.target.value }))}
                  />
                </div>
              </div>

              {editing && (
                <div className="space-y-1.5">
                  <Label htmlFor="company-status">Status</Label>
                  <Select
                    value={form.status}
                    onValueChange={(value) => value && setForm((f) => ({ ...f, status: value as CompanyStatus }))}
                  >
                    <SelectTrigger id="company-status" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {STATUSES.map((s) => (
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
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create company"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete company?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes <strong>{deleteTarget?.name}</strong>. Companies with job drives
                cannot be deleted.
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
