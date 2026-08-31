import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
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
import * as gradeBandService from "@/services/gradeBandService";
import type { GradeBandRequest, GradeBandResponse } from "@/types/academicOps";
import { extractErrorMessage, extractRawErrorMessage, parseFieldErrors } from "@/utils/apiError";

const FORM_FIELDS = ["grade", "minPercentage", "maxPercentage", "gradePoint", "passGrade", "description"] as const;

interface FormState {
  grade: string;
  minPercentage: string;
  maxPercentage: string;
  gradePoint: string;
  passGrade: "true" | "false";
  description: string;
}

const EMPTY_FORM: FormState = {
  grade: "",
  minPercentage: "",
  maxPercentage: "",
  gradePoint: "",
  passGrade: "true",
  description: "",
};

/**
 * Admin CRUD over `/api/grade-bands` (G7 — the grading scale, single source of truth).
 * The endpoint returns a plain, unpaginated array, so this does not use `useServerTable`.
 */
export default function GradeBandsPage() {
  const [bands, setBands] = useState<GradeBandResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  function loadBands() {
    setIsLoading(true);
    setLoadError(null);
    gradeBandService
      .listGradeBands()
      .then(setBands)
      .catch((err) => setLoadError(extractErrorMessage(err, "Failed to load grade bands.")))
      .finally(() => setIsLoading(false));
  }

  useEffect(() => {
    loadBands();
  }, []);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<GradeBandResponse | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [deleteTarget, setDeleteTarget] = useState<GradeBandResponse | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function openEdit(band: GradeBandResponse) {
    setEditing(band);
    setForm({
      grade: band.grade,
      minPercentage: String(band.minPercentage),
      maxPercentage: String(band.maxPercentage),
      gradePoint: String(band.gradePoint),
      passGrade: band.passGrade ? "true" : "false",
      description: band.description ?? "",
    });
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function validate(): boolean {
    const errors: Record<string, string> = {};
    if (!form.grade.trim()) errors.grade = "Grade letter is required.";
    else if (form.grade.trim().length > 5) errors.grade = "Grade letter must be 5 characters or fewer.";

    const min = Number(form.minPercentage);
    const max = Number(form.maxPercentage);
    const point = Number(form.gradePoint);

    if (form.minPercentage === "" || Number.isNaN(min) || min < 0 || min > 100) {
      errors.minPercentage = "Min percentage must be between 0 and 100.";
    }
    if (form.maxPercentage === "" || Number.isNaN(max) || max < 0 || max > 100) {
      errors.maxPercentage = "Max percentage must be between 0 and 100.";
    }
    if (!errors.minPercentage && !errors.maxPercentage && min > max) {
      errors.maxPercentage = "Max percentage must be greater than or equal to min percentage.";
    }
    if (form.gradePoint === "" || Number.isNaN(point) || point < 0 || point > 10) {
      errors.gradePoint = "Grade point must be between 0 and 10.";
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    const payload: GradeBandRequest = {
      grade: form.grade.trim(),
      minPercentage: Number(form.minPercentage),
      maxPercentage: Number(form.maxPercentage),
      gradePoint: Number(form.gradePoint),
      passGrade: form.passGrade === "true",
      description: form.description.trim() || null,
    };
    setIsSaving(true);
    try {
      if (editing) {
        await gradeBandService.updateGradeBand(editing.id, payload);
      } else {
        await gradeBandService.createGradeBand(payload);
      }
      setDialogOpen(false);
      loadBands();
    } catch (err) {
      const raw = extractRawErrorMessage(err);
      if (raw) {
        const parsed = parseFieldErrors(raw, FORM_FIELDS);
        setFieldErrors(parsed.fieldErrors);
        setFormError(parsed.formError);
      } else {
        setFormError(extractErrorMessage(err, "Failed to save the grade band."));
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
      await gradeBandService.deleteGradeBand(deleteTarget.id);
      setDeleteTarget(null);
      loadBands();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this grade band."));
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Grade bands</h1>
          <p className="text-muted-foreground">The percentage-to-grade scale used across every result.</p>
        </div>
        <Button onClick={openCreate}>
          <PlusIcon />
          Add grade band
        </Button>
      </div>

      <Alert>
        <AlertDescription>
          This table is the single source of truth for grading (G7) — nothing about a grade or
          grade point is hard-coded in the application. Editing or deleting a band changes every
          computed result that falls in its range, immediately.
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>All grade bands</CardTitle>
          <CardDescription>Ordered by minimum percentage, highest first.</CardDescription>
        </CardHeader>
        <CardContent className="px-0">
          {loadError && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{loadError}</AlertDescription>
              </Alert>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Grade</TableHead>
                <TableHead>Min %</TableHead>
                <TableHead>Max %</TableHead>
                <TableHead>Grade point</TableHead>
                <TableHead>Pass</TableHead>
                <TableHead>Description</TableHead>
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
              {!isLoading && bands.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    No grade bands configured yet.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                bands.map((band) => (
                  <TableRow key={band.id}>
                    <TableCell className="font-medium">{band.grade}</TableCell>
                    <TableCell>{band.minPercentage}</TableCell>
                    <TableCell>{band.maxPercentage}</TableCell>
                    <TableCell>{band.gradePoint}</TableCell>
                    <TableCell>
                      <Badge variant={band.passGrade ? "default" : "secondary"}>
                        {band.passGrade ? "Pass" : "Fail"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{band.description || "—"}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="icon-sm" onClick={() => openEdit(band)}>
                          <PencilIcon />
                          <span className="sr-only">Edit</span>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(band);
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
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <form onSubmit={handleSubmit}>
            <DialogHeader>
              <DialogTitle>{editing ? "Edit grade band" : "Add grade band"}</DialogTitle>
              <DialogDescription>
                {editing
                  ? `Update the ${editing.grade} band.`
                  : "Define a new percentage range and grade point."}
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              {formError && (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="band-grade">Grade</Label>
                <Input
                  id="band-grade"
                  value={form.grade}
                  maxLength={5}
                  onChange={(event) => setForm((f) => ({ ...f, grade: event.target.value }))}
                  aria-invalid={!!fieldErrors.grade}
                  placeholder="A+"
                />
                {fieldErrors.grade && <p className="text-xs text-destructive">{fieldErrors.grade}</p>}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="band-min">Min %</Label>
                  <Input
                    id="band-min"
                    type="number"
                    min={0}
                    max={100}
                    step="0.01"
                    value={form.minPercentage}
                    onChange={(event) => setForm((f) => ({ ...f, minPercentage: event.target.value }))}
                    aria-invalid={!!fieldErrors.minPercentage}
                  />
                  {fieldErrors.minPercentage && (
                    <p className="text-xs text-destructive">{fieldErrors.minPercentage}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="band-max">Max %</Label>
                  <Input
                    id="band-max"
                    type="number"
                    min={0}
                    max={100}
                    step="0.01"
                    value={form.maxPercentage}
                    onChange={(event) => setForm((f) => ({ ...f, maxPercentage: event.target.value }))}
                    aria-invalid={!!fieldErrors.maxPercentage}
                  />
                  {fieldErrors.maxPercentage && (
                    <p className="text-xs text-destructive">{fieldErrors.maxPercentage}</p>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="band-point">Grade point</Label>
                  <Input
                    id="band-point"
                    type="number"
                    min={0}
                    max={10}
                    step="0.01"
                    value={form.gradePoint}
                    onChange={(event) => setForm((f) => ({ ...f, gradePoint: event.target.value }))}
                    aria-invalid={!!fieldErrors.gradePoint}
                  />
                  {fieldErrors.gradePoint && (
                    <p className="text-xs text-destructive">{fieldErrors.gradePoint}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="band-pass">Counts as pass?</Label>
                  <Select
                    value={form.passGrade}
                    onValueChange={(value) =>
                      setForm((f) => ({ ...f, passGrade: (value ?? "true") as "true" | "false" }))
                    }
                  >
                    <SelectTrigger id="band-pass" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">Pass</SelectItem>
                      <SelectItem value="false">Fail</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="band-description">Description (optional)</Label>
                <Input
                  id="band-description"
                  value={form.description}
                  maxLength={100}
                  onChange={(event) => setForm((f) => ({ ...f, description: event.target.value }))}
                  aria-invalid={!!fieldErrors.description}
                  placeholder="Outstanding"
                />
                {fieldErrors.description && (
                  <p className="text-xs text-destructive">{fieldErrors.description}</p>
                )}
              </div>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : editing ? "Save changes" : "Create grade band"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete grade band?"
        description={
          <>
            {deleteError ? (
              <span className="text-destructive">{deleteError}</span>
            ) : (
              <>
                This permanently deletes the <strong>{deleteTarget?.grade}</strong> band. Any
                result that fell in this range will show no grade until a new band covers it.
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
