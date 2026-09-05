import { useEffect, useMemo, useState } from "react";
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
import * as announcementService from "@/services/announcementService";
import * as departmentService from "@/services/departmentService";
import type { DepartmentResponse } from "@/types/academic";
import type {
  AnnouncementAudience,
  AnnouncementCreateRequest,
  AnnouncementManageParams,
  AnnouncementResponse,
  AnnouncementUpdateRequest,
  NotificationPriority,
} from "@/types/realtime";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const ALL = "__ALL__";
const AUDIENCES: AnnouncementAudience[] = ["ALL", "STUDENTS", "FACULTY", "DEPARTMENT"];
const PRIORITIES: NotificationPriority[] = ["LOW", "NORMAL", "HIGH", "URGENT"];

const PRIORITY_VARIANT: Record<NotificationPriority, "outline" | "secondary" | "default" | "destructive"> = {
  LOW: "outline",
  NORMAL: "secondary",
  HIGH: "default",
  URGENT: "destructive",
};

/** `LocalDateTime` <-> `datetime-local` input value — no offset either side. */
function toDatetimeLocalValue(iso: string | null): string {
  if (!iso) return "";
  return iso.length >= 16 ? iso.slice(0, 16) : iso;
}
function fromDatetimeLocalValue(value: string): string | null {
  if (!value) return null;
  return value.length === 16 ? `${value}:00` : value;
}
function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

const CREATE_FIELDS = ["title", "body", "audience", "departmentId", "priority", "expiresAt"] as const;
const EDIT_FIELDS = ["title", "body", "priority", "expiresAt"] as const;

interface FormState {
  title: string;
  body: string;
  audience: AnnouncementAudience;
  departmentId: string;
  priority: NotificationPriority;
  expiresAt: string;
}

function emptyForm(): FormState {
  return { title: "", body: "", audience: "ALL", departmentId: "", priority: "NORMAL", expiresAt: "" };
}

function fromAnnouncement(a: AnnouncementResponse): FormState {
  return {
    title: a.title,
    body: a.body,
    audience: a.audience,
    departmentId: a.departmentId ? String(a.departmentId) : "",
    priority: a.priority,
    expiresAt: toDatetimeLocalValue(a.expiresAt),
  };
}

/**
 * `/admin/announcements` — ADMIN only: `GET /api/announcements/manage` with an
 * audience filter, an "include expired" toggle and search; create, edit (title/body/
 * priority/expiry only — audience and department are read-only on edit, see the
 * FIELD-DROP TRAP note in `announcementService.ts`), and delete with a confirm that
 * states the withdrawal effect.
 */
export default function AdminAnnouncementsPage() {
  const [audienceFilter, setAudienceFilter] = useState(ALL);
  const [includeExpired, setIncludeExpired] = useState(false);

  const filters = useMemo(
    () => ({
      audience: audienceFilter === ALL ? undefined : (audienceFilter as AnnouncementAudience),
      includeExpired: includeExpired || undefined,
    }),
    [audienceFilter, includeExpired],
  );

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable<
    AnnouncementResponse,
    Omit<AnnouncementManageParams, "search" | "page" | "size" | "sort">
  >(announcementService.listManaged, filters, { pageSize: 15, sort: "publishedAt,desc" });

  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [departmentsError, setDepartmentsError] = useState<string | null>(null);

  useEffect(() => {
    departmentService
      .listAllDepartments()
      .then(setDepartments)
      .catch((err) => setDepartmentsError(extractErrorMessage(err, "Failed to load departments.")));
  }, []);

  function departmentName(id: number | null): string | undefined {
    if (id === null) return undefined;
    return departments.find((d) => d.id === id)?.name;
  }

  // Create / edit dialog
  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState<"create" | "edit">("create");
  const [editTarget, setEditTarget] = useState<AnnouncementResponse | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  function openCreate() {
    setFormMode("create");
    setEditTarget(null);
    setForm(emptyForm());
    setFieldErrors({});
    setFormError(null);
    setFormOpen(true);
  }

  function openEdit(a: AnnouncementResponse) {
    setFormMode("edit");
    setEditTarget(a);
    setForm(fromAnnouncement(a));
    setFieldErrors({});
    setFormError(null);
    setFormOpen(true);
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.title.trim()) errors.title = "Title is required.";
    if (!form.body.trim()) errors.body = "Body is required.";
    if (formMode === "create" && form.audience === "DEPARTMENT" && !form.departmentId) {
      errors.departmentId = "A DEPARTMENT announcement requires a department.";
    }
    if (form.expiresAt) {
      const expires = fromDatetimeLocalValue(form.expiresAt);
      const publishedAt = formMode === "edit" ? editTarget?.publishedAt : null;
      // On create, publishedAt is set server-side to "now" — compare against the
      // current moment client-side only as a fast-fail; the server re-validates.
      const reference = publishedAt ? new Date(publishedAt) : new Date();
      if (expires && new Date(expires) <= reference) {
        errors.expiresAt = "Expiry must be after the publication time.";
      }
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
      if (formMode === "create") {
        const payload: AnnouncementCreateRequest = {
          title: form.title.trim(),
          body: form.body.trim(),
          audience: form.audience,
          departmentId: form.audience === "DEPARTMENT" ? Number(form.departmentId) : null,
          priority: form.priority,
          expiresAt: fromDatetimeLocalValue(form.expiresAt),
        };
        await announcementService.createAnnouncement(payload);
      } else {
        if (!editTarget) return;
        // Deliberately no audience / departmentId — see the FIELD-DROP TRAP note.
        const payload: AnnouncementUpdateRequest = {
          title: form.title.trim(),
          body: form.body.trim(),
          priority: form.priority,
          expiresAt: fromDatetimeLocalValue(form.expiresAt),
        };
        await announcementService.updateAnnouncement(editTarget.id, payload);
      }
      setFormOpen(false);
      refresh();
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, formMode === "create" ? CREATE_FIELDS : EDIT_FIELDS);
        setFieldErrors((prev) => ({ ...prev, ...parsed.fieldErrors }));
        setFormError(parsed.formError ?? (Object.keys(parsed.fieldErrors).length === 0 ? raw : null));
      } else {
        setFormError(extractErrorMessage(err, "Failed to save this announcement."));
      }
    } finally {
      setIsSaving(false);
    }
  }

  // Delete
  const [deleteTarget, setDeleteTarget] = useState<AnnouncementResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleDelete() {
    if (!deleteTarget) return;
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await announcementService.deleteAnnouncement(deleteTarget.id);
      setDeleteTarget(null);
      refresh();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this announcement."));
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Announcements</h1>
          <p className="text-muted-foreground">Publish and manage every announcement, expired or not.</p>
        </div>
        <Button type="button" onClick={openCreate}>
          <PlusIcon />
          New announcement
        </Button>
      </div>

      {departmentsError && (
        <Alert variant="destructive">
          <AlertDescription>{departmentsError}</AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>All announcements</CardTitle>
          <CardDescription>Search by title or body, filter by audience, or include expired ones.</CardDescription>
          <div className="flex flex-wrap items-center gap-2 pt-2">
            <Input
              placeholder="Search title or body…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-56"
            />
            <Select value={audienceFilter} onValueChange={(value) => value && setAudienceFilter(value)}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder="All audiences" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All audiences</SelectItem>
                {AUDIENCES.map((a) => (
                  <SelectItem key={a} value={a}>
                    {a}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button
              type="button"
              variant={includeExpired ? "default" : "outline"}
              size="sm"
              onClick={() => setIncludeExpired((v) => !v)}
            >
              Include expired
            </Button>
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
                <TableHead>Audience</TableHead>
                <TableHead>Priority</TableHead>
                <TableHead>Published</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Recipients</TableHead>
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
                    No announcements match these filters.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                data?.content.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell>
                      <div className="font-medium">{a.title}</div>
                      <div className="max-w-xs truncate text-xs text-muted-foreground">{a.body}</div>
                    </TableCell>
                    <TableCell>
                      <div>{a.audience}</div>
                      {a.departmentName && <div className="text-xs text-muted-foreground">{a.departmentName}</div>}
                    </TableCell>
                    <TableCell>
                      <Badge variant={PRIORITY_VARIANT[a.priority]}>{a.priority}</Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{formatDateTime(a.publishedAt)}</TableCell>
                    <TableCell>
                      <Badge variant={a.active ? "secondary" : "outline"}>{a.active ? "Active" : "Expired"}</Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {a.recipientCount ?? "—"}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon" onClick={() => openEdit(a)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(a);
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

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{formMode === "create" ? "New announcement" : "Edit announcement"}</DialogTitle>
              <DialogDescription>
                {formMode === "create"
                  ? "Publishes immediately and notifies every matching recipient."
                  : "Audience and department are fixed at creation — re-targeting means deleting and recreating this announcement."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="an-title">Title</Label>
                <Input
                  id="an-title"
                  value={form.title}
                  onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                  maxLength={200}
                  aria-invalid={!!fieldErrors.title}
                />
                {fieldErrors.title && <p className="text-xs text-destructive">{fieldErrors.title}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="an-body">Body</Label>
                <textarea
                  id="an-body"
                  className="min-h-28 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.body}
                  onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))}
                />
                {fieldErrors.body && <p className="text-xs text-destructive">{fieldErrors.body}</p>}
              </div>

              {formMode === "create" ? (
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <Label>Audience</Label>
                    <Select
                      value={form.audience}
                      onValueChange={(value) =>
                        value &&
                        setForm((f) => ({
                          ...f,
                          audience: value as AnnouncementAudience,
                          departmentId: value === "DEPARTMENT" ? f.departmentId : "",
                        }))
                      }
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {AUDIENCES.map((a) => (
                          <SelectItem key={a} value={a}>
                            {a}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  {form.audience === "DEPARTMENT" && (
                    <div className="space-y-1.5">
                      <Label>Department</Label>
                      <Select
                        value={form.departmentId}
                        onValueChange={(value) => value && setForm((f) => ({ ...f, departmentId: value }))}
                      >
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Select a department" />
                        </SelectTrigger>
                        <SelectContent>
                          {departments.map((d) => (
                            <SelectItem key={d.id} value={String(d.id)}>
                              {d.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {fieldErrors.departmentId && (
                        <p className="text-xs text-destructive">{fieldErrors.departmentId}</p>
                      )}
                    </div>
                  )}
                </div>
              ) : (
                editTarget && (
                  <div className="grid grid-cols-2 gap-3 rounded-lg border border-border bg-muted/30 p-3 text-sm">
                    <div>
                      <div className="text-xs text-muted-foreground">Audience (fixed)</div>
                      <div>{editTarget.audience}</div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">Department (fixed)</div>
                      <div>{editTarget.departmentName ?? departmentName(editTarget.departmentId) ?? "—"}</div>
                    </div>
                  </div>
                )
              )}

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label>Priority</Label>
                  <Select
                    value={form.priority}
                    onValueChange={(value) => value && setForm((f) => ({ ...f, priority: value as NotificationPriority }))}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PRIORITIES.map((p) => (
                        <SelectItem key={p} value={p}>
                          {p}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="an-expires">Expires (optional)</Label>
                  <Input
                    id="an-expires"
                    type="datetime-local"
                    value={form.expiresAt}
                    onChange={(e) => setForm((f) => ({ ...f, expiresAt: e.target.value }))}
                  />
                  {fieldErrors.expiresAt && <p className="text-xs text-destructive">{fieldErrors.expiresAt}</p>}
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setFormOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : formMode === "create" ? "Publish" : "Save changes"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete this announcement?"
        description={
          deleteError ? (
            <span className="text-destructive">{deleteError}</span>
          ) : (
            <>
              This withdraws "{deleteTarget?.title}" from every recipient's notification centre. This cannot be
              undone.
            </>
          )
        }
        confirmLabel="Delete"
        destructive
        isConfirming={isDeleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
