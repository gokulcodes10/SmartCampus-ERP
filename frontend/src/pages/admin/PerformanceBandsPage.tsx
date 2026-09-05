import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { PencilIcon } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import * as performanceBandService from "@/services/performanceBandService";
import type { PerformanceBandResponse } from "@/types/analytics";
import { extractErrorMessage } from "@/utils/apiError";

const CATEGORY_LABELS: Record<PerformanceBandResponse["category"], string> = {
  EXCELLENT: "Excellent",
  GOOD: "Good",
  AVERAGE: "Average",
  AT_RISK: "At risk",
};

interface FormState {
  minMarksPercentage: string;
  minAttendancePercentage: string;
  minGpa: string;
  colorHex: string;
  description: string;
}

function toFormState(band: PerformanceBandResponse): FormState {
  return {
    minMarksPercentage: String(band.minMarksPercentage),
    minAttendancePercentage: String(band.minAttendancePercentage),
    minGpa: band.minGpa == null ? "" : String(band.minGpa),
    colorHex: band.colorHex,
    description: band.description ?? "",
  };
}

const HEX_PATTERN = /^#[0-9A-Fa-f]{6}$/;

/**
 * Admin screen for the four fixed performance bands (`/api/performance-bands`) —
 * this is what makes the classification thresholds configurable instead of
 * hard-coded (§60). The category set is closed: the backend exposes no create or
 * delete, so this page renders neither button (§69 — no button that does nothing).
 */
export default function PerformanceBandsPage() {
  const [bands, setBands] = useState<PerformanceBandResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  function loadBands() {
    performanceBandService
      .list()
      .then(setBands)
      .catch((err) => setLoadError(extractErrorMessage(err, "Failed to load performance bands.")))
      .finally(() => setIsLoading(false));
  }

  // Mount-only fetch: isLoading/loadError already start at their reset values via
  // useState above, so the effect only needs to perform the fetch itself.
  useEffect(() => {
    loadBands();
  }, []);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<PerformanceBandResponse | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  function openEdit(band: PerformanceBandResponse) {
    setEditing(band);
    setForm(toFormState(band));
    setFieldErrors({});
    setFormError(null);
    setDialogOpen(true);
  }

  function validate(current: FormState): { fieldErrors: Record<string, string>; ok: boolean } {
    const errors: Record<string, string> = {};

    const marks = Number(current.minMarksPercentage);
    if (current.minMarksPercentage.trim() === "" || Number.isNaN(marks) || marks < 0 || marks > 100) {
      errors.minMarksPercentage = "Must be between 0 and 100.";
    }

    const attendance = Number(current.minAttendancePercentage);
    if (
      current.minAttendancePercentage.trim() === "" ||
      Number.isNaN(attendance) ||
      attendance < 0 ||
      attendance > 100
    ) {
      errors.minAttendancePercentage = "Must be between 0 and 100.";
    }

    if (current.minGpa.trim() !== "") {
      const gpa = Number(current.minGpa);
      if (Number.isNaN(gpa) || gpa < 0 || gpa > 10) {
        errors.minGpa = "Must be between 0 and 10, or left blank for no GPA requirement.";
      }
    }

    if (!HEX_PATTERN.test(current.colorHex.trim())) {
      errors.colorHex = "Must be a hex color like #16A34A.";
    }

    if (current.description.length > 150) {
      errors.description = "Must be 150 characters or fewer.";
    }

    return { fieldErrors: errors, ok: Object.keys(errors).length === 0 };
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!editing || !form) return;

    const { fieldErrors: errors, ok } = validate(form);
    setFieldErrors(errors);
    if (!ok) return;

    setIsSaving(true);
    try {
      const updated = await performanceBandService.update(editing.id, {
        minMarksPercentage: Number(form.minMarksPercentage),
        minAttendancePercentage: Number(form.minAttendancePercentage),
        minGpa: form.minGpa.trim() === "" ? null : Number(form.minGpa),
        colorHex: form.colorHex.trim(),
        description: form.description.trim() === "" ? null : form.description.trim(),
      });
      // Re-render from the server's own response, not the submitted form — a field
      // the backend silently ignored would otherwise look like it saved (trap 7).
      setBands((prev) => prev.map((b) => (b.id === updated.id ? updated : b)));
      setDialogOpen(false);
    } catch (err) {
      setFormError(extractErrorMessage(err, "Failed to save this performance band."));
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Performance bands</h1>
        <p className="text-muted-foreground">
          The thresholds used to classify every student as Excellent, Good, Average or At risk.
        </p>
      </div>

      <Alert>
        <AlertDescription>
          This table is the single source of truth for performance classification (§60) —
          nothing about a student&apos;s classification is hard-coded. The four categories are
          fixed; only their thresholds, color and description can be changed.
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>All performance bands</CardTitle>
          <CardDescription>Ordered strictest first — the last row is the catch-all.</CardDescription>
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
                <TableHead>Category</TableHead>
                <TableHead>Min marks %</TableHead>
                <TableHead>Min attendance %</TableHead>
                <TableHead>Min GPA</TableHead>
                <TableHead>Color</TableHead>
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
              {!isLoading && bands.length === 0 && !loadError && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    No performance bands configured.
                  </TableCell>
                </TableRow>
              )}
              {!isLoading &&
                bands.map((band) => (
                  <TableRow key={band.id}>
                    <TableCell className="font-medium">{CATEGORY_LABELS[band.category]}</TableCell>
                    <TableCell>{band.minMarksPercentage}</TableCell>
                    <TableCell>{band.minAttendancePercentage}</TableCell>
                    <TableCell>{band.minGpa == null ? "—" : band.minGpa}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span
                          className="size-4 shrink-0 rounded-full border border-border"
                          style={{ backgroundColor: band.colorHex }}
                        />
                        <span className="text-muted-foreground">{band.colorHex}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{band.description || "—"}</TableCell>
                    <TableCell className="text-right">
                      <Button variant="ghost" size="icon" onClick={() => openEdit(band)}>
                        <PencilIcon />
                        <span className="sr-only">Edit</span>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          {form && (
            <form onSubmit={handleSubmit}>
              <DialogHeader>
                <DialogTitle>Edit {editing ? CATEGORY_LABELS[editing.category] : ""} band</DialogTitle>
                <DialogDescription>
                  The category and its position in the scale are fixed — only thresholds, color
                  and description can change.
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-4 py-4">
                {formError && (
                  <Alert variant="destructive">
                    <AlertDescription>{formError}</AlertDescription>
                  </Alert>
                )}

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="pb-marks">Min marks %</Label>
                    <Input
                      id="pb-marks"
                      type="number"
                      min={0}
                      max={100}
                      step="0.01"
                      value={form.minMarksPercentage}
                      onChange={(event) =>
                        setForm((f) => (f ? { ...f, minMarksPercentage: event.target.value } : f))
                      }
                      aria-invalid={!!fieldErrors.minMarksPercentage}
                    />
                    {fieldErrors.minMarksPercentage && (
                      <p className="text-xs text-destructive">{fieldErrors.minMarksPercentage}</p>
                    )}
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="pb-attendance">Min attendance %</Label>
                    <Input
                      id="pb-attendance"
                      type="number"
                      min={0}
                      max={100}
                      step="0.01"
                      value={form.minAttendancePercentage}
                      onChange={(event) =>
                        setForm((f) => (f ? { ...f, minAttendancePercentage: event.target.value } : f))
                      }
                      aria-invalid={!!fieldErrors.minAttendancePercentage}
                    />
                    {fieldErrors.minAttendancePercentage && (
                      <p className="text-xs text-destructive">{fieldErrors.minAttendancePercentage}</p>
                    )}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="pb-gpa">Min GPA (optional)</Label>
                  <Input
                    id="pb-gpa"
                    type="number"
                    min={0}
                    max={10}
                    step="0.01"
                    placeholder="No GPA requirement"
                    value={form.minGpa}
                    onChange={(event) => setForm((f) => (f ? { ...f, minGpa: event.target.value } : f))}
                    aria-invalid={!!fieldErrors.minGpa}
                  />
                  <p className="text-xs text-muted-foreground">
                    Leave blank for &quot;no GPA requirement&quot; — it will not be coerced to 0.
                  </p>
                  {fieldErrors.minGpa && <p className="text-xs text-destructive">{fieldErrors.minGpa}</p>}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="pb-color">Color</Label>
                  <div className="flex items-center gap-2">
                    <input
                      type="color"
                      aria-label="Pick color"
                      className="h-8 w-10 shrink-0 cursor-pointer rounded-md border border-input bg-transparent p-0.5"
                      value={HEX_PATTERN.test(form.colorHex) ? form.colorHex : "#000000"}
                      onChange={(event) => setForm((f) => (f ? { ...f, colorHex: event.target.value } : f))}
                    />
                    <Input
                      id="pb-color"
                      value={form.colorHex}
                      maxLength={7}
                      placeholder="#16A34A"
                      onChange={(event) => setForm((f) => (f ? { ...f, colorHex: event.target.value } : f))}
                      aria-invalid={!!fieldErrors.colorHex}
                    />
                  </div>
                  {fieldErrors.colorHex && <p className="text-xs text-destructive">{fieldErrors.colorHex}</p>}
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="pb-description">Description (optional)</Label>
                  <Input
                    id="pb-description"
                    value={form.description}
                    maxLength={150}
                    onChange={(event) => setForm((f) => (f ? { ...f, description: event.target.value } : f))}
                    aria-invalid={!!fieldErrors.description}
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
                  {isSaving ? "Saving…" : "Save changes"}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
