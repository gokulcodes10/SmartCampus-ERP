import { useEffect, useState } from "react";
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
import * as facultyService from "@/services/facultyService";
import type { FacultyResponse } from "@/types/academic";
import type {
  AnnouncementCreateRequest,
  AnnouncementManageParams,
  AnnouncementResponse,
  AnnouncementUpdateRequest,
  NotificationPriority,
} from "@/types/realtime";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

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

const CREATE_FIELDS = ["title", "body", "priority", "expiresAt"] as const;
const EDIT_FIELDS = ["title", "body", "priority", "expiresAt"] as const;

interface FormState {
  title: string;
  body: string;
  priority: NotificationPriority;
  expiresAt: string;
}

function emptyForm(): FormState {
  return { title: "", body: "", priority: "NORMAL", expiresAt: "" };
}

function fromAnnouncement(a: AnnouncementResponse): FormState {
  return {
    title: a.title,
    body: a.body,
    priority: a.priority,
    expiresAt: toDatetimeLocalValue(a.expiresAt),
  };
}

/**
 * `/faculty/announcements` — the FACULTY half of §42 ("admin/faculty-authorized users
 * can create announcements"), deliberately narrower than `AdminAnnouncementsPage`.
 *
 * Two controls the admin screen has are absent here, and their absence is the point:
 *
 *  - **No audience selector.** A faculty member may only ever create a DEPARTMENT
 *    announcement. Rendering ALL/STUDENTS/FACULTY options would be offering choices the
 *    server answers with 403 — a §69 control that does nothing.
 *  - **No department selector.** The only legal department is the caller's own, so the
 *    request omits `departmentId` and lets the server fill it in. The department is shown
 *    as read-only text (from `GET /api/faculty/me`) so the target is never a mystery,
 *    but it is text rather than a control because there is nothing to choose between.
 *
 * The listing is `GET /api/announcements/manage`, which the SERVER scopes to the
 * caller's own announcements for a faculty user. This page sends no "mine" flag — the
 * scoping is not the client's decision to make, and a flag would imply it were.
 *
 * Editing carries no audience/departmentId, same as the admin screen — see the
 * FIELD-DROP TRAP note in `announcementService.ts`. Re-targeting is delete + recreate.
 */
export default function FacultyAnnouncementsPage() {
  const [includeExpired, setIncludeExpired] = useState(true);

  const { data, isLoading, error, setPage, search, setSearch, refresh } = useServerTable<
    AnnouncementResponse,
    Omit<AnnouncementManageParams, "search" | "page" | "size" | "sort">
  >(
    announcementService.listManaged,
    { includeExpired: includeExpired || undefined },
    { pageSize: 15, sort: "publishedAt,desc" },
  );

  // The caller's own department, for display only. A FACULTY account with no faculty
  // profile row is a real state (an account can be provisioned before its profile is
  // created), and the backend refuses the write in that case — so surface it as a
  // blocking explanation rather than letting the publish button fail mysteriously.
  const [profile, setProfile] = useState<FacultyResponse | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  useEffect(() => {
    facultyService
      .getMyFacultyProfile()
      .then(setProfile)
      .catch((err) =>
        setProfileError(
          extractErrorMessage(
            err,
            "Could not load your faculty profile, so the department for a new announcement is unknown.",
          ),
        ),
      );
  }, []);

  const canCompose = profile !== null;

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
    if (form.expiresAt) {
      const expires = fromDatetimeLocalValue(form.expiresAt);
      const publishedAt = formMode === "edit" ? editTarget?.publishedAt : null;
      // On create the server stamps publishedAt as "now"; comparing against the current
      // moment here is only a fast-fail, and the server re-validates either way.
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
        // audience is fixed to DEPARTMENT and departmentId is omitted on purpose: the
        // server resolves the caller's own department, the only value it would accept.
        const payload: AnnouncementCreateRequest = {
          title: form.title.trim(),
          body: form.body.trim(),
          audience: "DEPARTMENT",
          priority: form.priority,
          expiresAt: fromDatetimeLocalValue(form.expiresAt),
        };
        await announcementService.createAnnouncement(payload);
      } else {
        if (!editTarget) return;
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
          <h1 className="text-2xl font-semibold tracking-tight">My announcements</h1>
          <p className="text-muted-foreground">
            {profile
              ? `Announcements you have published to the ${profile.departmentName} department.`
              : "Announcements you have published to your own department."}
          </p>
        </div>
        <Button type="button" onClick={openCreate} disabled={!canCompose}>
          <PlusIcon />
          New announcement
        </Button>
      </div>

      {profileError && (
        <Alert variant="destructive">
          <AlertDescription>
            {profileError} You can still read and manage announcements you have already published.
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Published by you</CardTitle>
          <CardDescription>
            Only your own announcements appear here. Institution-wide announcements are an administrator's to
            publish, and you can read those on the announcements board.
          </CardDescription>
          <div className="flex flex-wrap items-center gap-2 pt-2">
            <Input
              placeholder="Search title or body…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-56"
            />
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

          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Department</TableHead>
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
                      You have not published any announcements yet.
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
                      <TableCell className="text-sm">{a.departmentName ?? "—"}</TableCell>
                      <TableCell>
                        <Badge variant={PRIORITY_VARIANT[a.priority]}>{a.priority}</Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">{formatDateTime(a.publishedAt)}</TableCell>
                      <TableCell>
                        <Badge variant={a.active ? "secondary" : "outline"}>{a.active ? "Active" : "Expired"}</Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">{a.recipientCount ?? "—"}</TableCell>
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
          </div>

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
                  ? "Publishes immediately to your department and appears in every member's notification centre."
                  : "The department is fixed at creation — re-targeting means deleting and recreating this announcement."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="rounded-lg border border-border bg-muted/30 p-3 text-sm">
                <div className="text-xs text-muted-foreground">Audience</div>
                <div>
                  {formMode === "edit"
                    ? (editTarget?.departmentName ?? "Your department")
                    : (profile?.departmentName ?? "Your department")}{" "}
                  department
                </div>
                <p className="pt-1 text-xs text-muted-foreground">
                  Faculty announcements always go to your own department. Wider audiences are published by an
                  administrator.
                </p>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="fac-an-title">Title</Label>
                <Input
                  id="fac-an-title"
                  value={form.title}
                  onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
                  maxLength={200}
                  aria-invalid={!!fieldErrors.title}
                />
                {fieldErrors.title && <p className="text-xs text-destructive">{fieldErrors.title}</p>}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="fac-an-body">Body</Label>
                <textarea
                  id="fac-an-body"
                  className="min-h-28 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.body}
                  onChange={(e) => setForm((f) => ({ ...f, body: e.target.value }))}
                  aria-invalid={!!fieldErrors.body}
                />
                {fieldErrors.body && <p className="text-xs text-destructive">{fieldErrors.body}</p>}
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label>Priority</Label>
                  <Select
                    value={form.priority}
                    onValueChange={(value) =>
                      value && setForm((f) => ({ ...f, priority: value as NotificationPriority }))
                    }
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
                  <Label htmlFor="fac-an-expires">Expires (optional)</Label>
                  <Input
                    id="fac-an-expires"
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
