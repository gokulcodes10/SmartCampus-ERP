import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { ArrowDownIcon, ArrowUpIcon, PencilIcon, Trash2Icon } from "lucide-react";

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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import * as aiService from "@/services/aiService";
import type { AIStudyPlanItemRequest, AIStudyPlanItemResponse, AIStudyPlanResponse, AIStudyPlanStatus } from "@/types/ai";
import { extractErrorMessage } from "@/utils/apiError";

const STATUSES: AIStudyPlanStatus[] = ["ACTIVE", "COMPLETED", "ARCHIVED"];

function formatNullable(value: number | string | null | undefined, suffix = ""): string {
  return value === null || value === undefined ? "—" : `${value}${suffix}`;
}

interface PlanFormState {
  title: string;
  goal: string;
  startDate: string;
  endDate: string;
  status: AIStudyPlanStatus;
}

function planFormFrom(plan: AIStudyPlanResponse): PlanFormState {
  return {
    title: plan.title,
    goal: plan.goal ?? "",
    startDate: plan.startDate,
    endDate: plan.endDate,
    status: plan.status,
  };
}

interface ItemFormState {
  subjectId: string;
  subjectLabel: string;
  scheduledDate: string;
  title: string;
  description: string;
  durationMinutes: string;
}

function emptyItemForm(): ItemFormState {
  return { subjectId: "", subjectLabel: "", scheduledDate: "", title: "", description: "", durationMinutes: "" };
}

function itemFormFrom(item: AIStudyPlanItemResponse): ItemFormState {
  return {
    subjectId: item.subjectId !== null ? String(item.subjectId) : "",
    subjectLabel: item.subjectLabel ?? "",
    scheduledDate: item.scheduledDate,
    title: item.title,
    description: item.description ?? "",
    durationMinutes: item.durationMinutes !== null ? String(item.durationMinutes) : "",
  };
}

/** Builds a full item request from an existing item plus overrides — every PUT resends
 * the item's current fields so an unrelated edit (e.g. toggling `completed`) never
 * silently clears `subjectLabel`/`description`/etc. */
function fullItemRequest(
  item: AIStudyPlanItemResponse,
  overrides: Partial<AIStudyPlanItemRequest> = {},
): AIStudyPlanItemRequest {
  return {
    subjectId: item.subjectId ?? undefined,
    subjectLabel: item.subjectLabel ?? undefined,
    scheduledDate: item.scheduledDate,
    title: item.title,
    description: item.description ?? undefined,
    durationMinutes: item.durationMinutes ?? undefined,
    completed: item.completed,
    position: item.position,
    ...overrides,
  };
}

interface StudyPlanEditorProps {
  planId: number;
  onClose: () => void;
  /** Fires after any successful write so the parent's summary list can refresh. */
  onChanged: () => void;
}

/**
 * Edits one study plan: title/goal/dates/status, and its items — add, edit, delete,
 * reorder by position, and tick complete. An AI-generated plan is labelled advisory
 * (it is a suggestion, not a guaranteed outcome) and "edited by you" once `edited` is
 * true, per the Phase 6 semantics (any successful write on an AI_GENERATED plan sets it).
 */
export function StudyPlanEditor({ planId, onClose, onChanged }: StudyPlanEditorProps) {
  const [plan, setPlan] = useState<AIStudyPlanResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [planForm, setPlanForm] = useState<PlanFormState | null>(null);
  const [savingPlan, setSavingPlan] = useState(false);
  const [planFormError, setPlanFormError] = useState<string | null>(null);

  const [deletePlanOpen, setDeletePlanOpen] = useState(false);
  const [deletingPlan, setDeletingPlan] = useState(false);
  const [deletePlanError, setDeletePlanError] = useState<string | null>(null);

  const [itemDialogOpen, setItemDialogOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<AIStudyPlanItemResponse | null>(null);
  const [itemForm, setItemForm] = useState<ItemFormState>(emptyItemForm());
  const [itemFormError, setItemFormError] = useState<string | null>(null);
  const [savingItem, setSavingItem] = useState(false);

  const [deleteItemTarget, setDeleteItemTarget] = useState<AIStudyPlanItemResponse | null>(null);
  const [deletingItem, setDeletingItem] = useState(false);
  const [deleteItemError, setDeleteItemError] = useState<string | null>(null);

  const [busyItemId, setBusyItemId] = useState<number | null>(null);
  const [itemActionError, setItemActionError] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    aiService
      .getStudyPlan(planId)
      .then((data) => {
        setPlan(data);
        setPlanForm(planFormFrom(data));
      })
      .catch((err) => setError(extractErrorMessage(err, "Failed to load the study plan.")))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [planId]);

  async function handleSavePlan(event: FormEvent) {
    event.preventDefault();
    if (!planForm) return;
    setSavingPlan(true);
    setPlanFormError(null);
    try {
      const updated = await aiService.updateStudyPlan(planId, {
        title: planForm.title.trim(),
        goal: planForm.goal.trim() ? planForm.goal.trim() : undefined,
        startDate: planForm.startDate,
        endDate: planForm.endDate,
        status: planForm.status,
      });
      setPlan((prev) => (prev ? { ...updated, items: prev.items } : updated));
      onChanged();
    } catch (err) {
      setPlanFormError(extractErrorMessage(err, "Failed to save the plan."));
    } finally {
      setSavingPlan(false);
    }
  }

  async function handleDeletePlan() {
    setDeletingPlan(true);
    setDeletePlanError(null);
    try {
      await aiService.deleteStudyPlan(planId);
      onChanged();
      onClose();
    } catch (err) {
      setDeletePlanError(extractErrorMessage(err, "Failed to delete the plan."));
    } finally {
      setDeletingPlan(false);
    }
  }

  function openAddItem() {
    setEditingItem(null);
    setItemForm(emptyItemForm());
    setItemFormError(null);
    setItemDialogOpen(true);
  }

  function openEditItem(item: AIStudyPlanItemResponse) {
    setEditingItem(item);
    setItemForm(itemFormFrom(item));
    setItemFormError(null);
    setItemDialogOpen(true);
  }

  async function handleSaveItem(event: FormEvent) {
    event.preventDefault();
    const title = itemForm.title.trim();
    if (!title) {
      setItemFormError("Title is required.");
      return;
    }
    if (!itemForm.scheduledDate) {
      setItemFormError("Scheduled date is required.");
      return;
    }
    const subjectId = itemForm.subjectId.trim() ? Number(itemForm.subjectId.trim()) : undefined;
    const durationMinutes = itemForm.durationMinutes.trim() ? Number(itemForm.durationMinutes.trim()) : undefined;

    const payload: AIStudyPlanItemRequest = {
      subjectId,
      subjectLabel: itemForm.subjectLabel.trim() ? itemForm.subjectLabel.trim() : undefined,
      scheduledDate: itemForm.scheduledDate,
      title,
      description: itemForm.description.trim() ? itemForm.description.trim() : undefined,
      durationMinutes,
      completed: editingItem?.completed,
      position: editingItem?.position,
    };

    setSavingItem(true);
    setItemFormError(null);
    try {
      const updated = editingItem
        ? await aiService.updateStudyPlanItem(planId, editingItem.id, payload)
        : await aiService.addStudyPlanItem(planId, payload);
      setPlan(updated);
      setItemDialogOpen(false);
      onChanged();
    } catch (err) {
      setItemFormError(extractErrorMessage(err, "Failed to save the item."));
    } finally {
      setSavingItem(false);
    }
  }

  async function handleDeleteItem() {
    if (!deleteItemTarget) return;
    setDeletingItem(true);
    setDeleteItemError(null);
    try {
      const updated = await aiService.deleteStudyPlanItem(planId, deleteItemTarget.id);
      setPlan(updated);
      setDeleteItemTarget(null);
      onChanged();
    } catch (err) {
      setDeleteItemError(extractErrorMessage(err, "Failed to delete the item."));
    } finally {
      setDeletingItem(false);
    }
  }

  async function toggleCompleted(item: AIStudyPlanItemResponse) {
    setBusyItemId(item.id);
    setItemActionError(null);
    try {
      const updated = await aiService.updateStudyPlanItem(
        planId,
        item.id,
        fullItemRequest(item, { completed: !item.completed }),
      );
      setPlan(updated);
      onChanged();
    } catch (err) {
      setItemActionError(extractErrorMessage(err, "Failed to update the item."));
    } finally {
      setBusyItemId(null);
    }
  }

  async function moveItem(item: AIStudyPlanItemResponse, direction: "up" | "down") {
    if (!plan) return;
    const sorted = [...plan.items].sort((a, b) => a.position - b.position);
    const idx = sorted.findIndex((i) => i.id === item.id);
    const swapIdx = direction === "up" ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;
    const other = sorted[swapIdx];

    setBusyItemId(item.id);
    setItemActionError(null);
    try {
      const tempPosition = Math.max(...sorted.map((i) => i.position)) + 1;
      await aiService.updateStudyPlanItem(planId, item.id, fullItemRequest(item, { position: tempPosition }));
      await aiService.updateStudyPlanItem(planId, other.id, fullItemRequest(other, { position: item.position }));
      const finalPlan = await aiService.updateStudyPlanItem(
        planId,
        item.id,
        fullItemRequest(item, { position: other.position }),
      );
      setPlan(finalPlan);
      onChanged();
    } catch (err) {
      setItemActionError(extractErrorMessage(err, "Failed to reorder items."));
    } finally {
      setBusyItemId(null);
    }
  }

  if (loading) {
    return (
      <Card>
        <CardContent className="py-8 text-center text-muted-foreground">Loading study plan…</CardContent>
      </Card>
    );
  }

  if (error || !plan || !planForm) {
    return (
      <Card>
        <CardContent className="space-y-3 py-6">
          <Alert variant="destructive">
            <AlertDescription>{error ?? "Study plan not found."}</AlertDescription>
          </Alert>
          <Button variant="outline" size="sm" onClick={onClose}>
            Back to plans
          </Button>
        </CardContent>
      </Card>
    );
  }

  const items = [...plan.items].sort((a, b) => a.position - b.position);

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center gap-2">
            <CardTitle>{plan.title}</CardTitle>
            <Badge variant={plan.planType === "REVISION_SCHEDULE" ? "secondary" : "default"}>
              {plan.planType === "REVISION_SCHEDULE" ? "Revision schedule" : "Study plan"}
            </Badge>
            {plan.source === "AI_GENERATED" && <Badge variant="outline">AI-generated · advisory</Badge>}
            {plan.edited && <Badge variant="secondary">Edited by you</Badge>}
          </div>
          <CardDescription>
            {plan.source === "AI_GENERATED"
              ? `Suggested by ${plan.model ?? "the assistant"} — review before you rely on it.`
              : "Created by you."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSavePlan} className="space-y-4">
            {planFormError && (
              <Alert variant="destructive">
                <AlertDescription>{planFormError}</AlertDescription>
              </Alert>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="plan-title">Title</Label>
              <Input
                id="plan-title"
                value={planForm.title}
                maxLength={150}
                onChange={(event) => setPlanForm((f) => (f ? { ...f, title: event.target.value } : f))}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="plan-goal">Goal</Label>
              <Input
                id="plan-goal"
                value={planForm.goal}
                maxLength={500}
                onChange={(event) => setPlanForm((f) => (f ? { ...f, goal: event.target.value } : f))}
                placeholder="Optional"
              />
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label htmlFor="plan-start">Start date</Label>
                <Input
                  id="plan-start"
                  type="date"
                  value={planForm.startDate}
                  onChange={(event) => setPlanForm((f) => (f ? { ...f, startDate: event.target.value } : f))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="plan-end">End date</Label>
                <Input
                  id="plan-end"
                  type="date"
                  value={planForm.endDate}
                  onChange={(event) => setPlanForm((f) => (f ? { ...f, endDate: event.target.value } : f))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="plan-status">Status</Label>
                <Select
                  value={planForm.status}
                  onValueChange={(value) =>
                    value && setPlanForm((f) => (f ? { ...f, status: value as AIStudyPlanStatus } : f))
                  }
                >
                  <SelectTrigger id="plan-status" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {STATUSES.map((status) => (
                      <SelectItem key={status} value={status}>
                        {status}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={savingPlan}>
                {savingPlan ? "Saving…" : "Save changes"}
              </Button>
              <Button type="button" variant="outline" onClick={onClose}>
                Back to plans
              </Button>
              <Button
                type="button"
                variant="destructive"
                className="sm:ml-auto"
                onClick={() => setDeletePlanOpen(true)}
              >
                Delete plan
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Items</CardTitle>
              <CardDescription>
                {items.length} item{items.length === 1 ? "" : "s"} ·{" "}
                {items.filter((i) => i.completed).length} completed
              </CardDescription>
            </div>
            <Button type="button" size="sm" onClick={openAddItem}>
              Add item
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-2">
          {itemActionError && (
            <Alert variant="destructive">
              <AlertDescription>{itemActionError}</AlertDescription>
            </Alert>
          )}
          {items.length === 0 && (
            <p className="py-6 text-center text-sm text-muted-foreground">No items yet.</p>
          )}
          {items.map((item, index) => (
            <div
              key={item.id}
              className="flex flex-col gap-2 rounded-lg border border-border p-3 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs text-muted-foreground">#{item.position}</span>
                  <span className="font-medium">{item.title}</span>
                  {item.completed && <Badge variant="secondary">Completed</Badge>}
                </div>
                <p className="text-xs text-muted-foreground">
                  {item.scheduledDate}
                  {(item.subjectCode || item.subjectLabel) && (
                    <> · {item.subjectCode ? `${item.subjectCode} — ${item.subjectName}` : item.subjectLabel}</>
                  )}
                  {item.durationMinutes !== null && <> · {item.durationMinutes} min</>}
                </p>
                {item.description && <p className="mt-1 text-sm">{item.description}</p>}
              </div>
              <div className="flex shrink-0 flex-wrap items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  disabled={index === 0 || busyItemId === item.id}
                  onClick={() => moveItem(item, "up")}
                >
                  <ArrowUpIcon />
                  <span className="sr-only">Move up</span>
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  disabled={index === items.length - 1 || busyItemId === item.id}
                  onClick={() => moveItem(item, "down")}
                >
                  <ArrowDownIcon />
                  <span className="sr-only">Move down</span>
                </Button>
                <Button
                  type="button"
                  variant={item.completed ? "secondary" : "outline"}
                  size="sm"
                  disabled={busyItemId === item.id}
                  onClick={() => toggleCompleted(item)}
                >
                  {item.completed ? "Mark incomplete" : "Mark complete"}
                </Button>
                <Button type="button" variant="ghost" size="icon-sm" onClick={() => openEditItem(item)}>
                  <PencilIcon />
                  <span className="sr-only">Edit</span>
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  onClick={() => {
                    setDeleteItemError(null);
                    setDeleteItemTarget(item);
                  }}
                >
                  <Trash2Icon />
                  <span className="sr-only">Delete</span>
                </Button>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <Dialog open={itemDialogOpen} onOpenChange={setItemDialogOpen}>
        <DialogContent>
          <form onSubmit={handleSaveItem}>
            <DialogHeader>
              <DialogTitle>{editingItem ? "Edit item" : "Add item"}</DialogTitle>
              <DialogDescription>
                {formatNullable(plan.startDate)} – {formatNullable(plan.endDate)}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              {itemFormError && (
                <Alert variant="destructive">
                  <AlertDescription>{itemFormError}</AlertDescription>
                </Alert>
              )}
              <div className="space-y-1.5">
                <Label htmlFor="item-title">Title</Label>
                <Input
                  id="item-title"
                  value={itemForm.title}
                  maxLength={200}
                  onChange={(event) => setItemForm((f) => ({ ...f, title: event.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="item-date">Scheduled date</Label>
                <Input
                  id="item-date"
                  type="date"
                  value={itemForm.scheduledDate}
                  onChange={(event) => setItemForm((f) => ({ ...f, scheduledDate: event.target.value }))}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="item-subject-id">Subject ID</Label>
                  <Input
                    id="item-subject-id"
                    type="number"
                    min={1}
                    value={itemForm.subjectId}
                    onChange={(event) => setItemForm((f) => ({ ...f, subjectId: event.target.value }))}
                    placeholder="Optional"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="item-duration">Duration (minutes)</Label>
                  <Input
                    id="item-duration"
                    type="number"
                    min={1}
                    max={1440}
                    value={itemForm.durationMinutes}
                    onChange={(event) => setItemForm((f) => ({ ...f, durationMinutes: event.target.value }))}
                    placeholder="Optional"
                  />
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="item-subject-label">Subject label</Label>
                <Input
                  id="item-subject-label"
                  value={itemForm.subjectLabel}
                  maxLength={150}
                  onChange={(event) => setItemForm((f) => ({ ...f, subjectLabel: event.target.value }))}
                  placeholder="Free-text subject name, if no subject ID"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="item-description">Description</Label>
                <textarea
                  id="item-description"
                  value={itemForm.description}
                  onChange={(event) => setItemForm((f) => ({ ...f, description: event.target.value }))}
                  rows={3}
                  className="w-full rounded-lg border border-input bg-transparent px-2.5 py-1.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
                  placeholder="Optional"
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setItemDialogOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={savingItem}>
                {savingItem ? "Saving…" : editingItem ? "Save changes" : "Add item"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deletePlanOpen}
        onOpenChange={setDeletePlanOpen}
        title="Delete this plan?"
        description={
          deletePlanError ? (
            <span className="text-destructive">{deletePlanError}</span>
          ) : (
            <>This permanently deletes "{plan.title}" and every item in it.</>
          )
        }
        confirmLabel="Delete"
        destructive
        isConfirming={deletingPlan}
        onConfirm={handleDeletePlan}
      />

      <ConfirmDialog
        open={deleteItemTarget !== null}
        onOpenChange={(open) => !open && setDeleteItemTarget(null)}
        title="Delete this item?"
        description={
          deleteItemError ? (
            <span className="text-destructive">{deleteItemError}</span>
          ) : (
            <>This permanently deletes "{deleteItemTarget?.title}".</>
          )
        }
        confirmLabel="Delete"
        destructive
        isConfirming={deletingItem}
        onConfirm={handleDeleteItem}
      />
    </div>
  );
}
