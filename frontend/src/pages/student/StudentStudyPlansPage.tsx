import { useState } from "react";

import { PaginationBar } from "@/components/admin/PaginationBar";
import { StudyPlanEditor } from "@/components/ai/StudyPlanEditor";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useServerTable } from "@/hooks/useServerTable";
import * as aiService from "@/services/aiService";
import type { AIStudyPlanStatus, AIStudyPlanSummaryResponse, AIStudyPlanType } from "@/types/ai";
import { extractErrorMessage } from "@/utils/apiError";

const ALL = "__ALL__";
const PLAN_TYPES: AIStudyPlanType[] = ["STUDY_PLAN", "REVISION_SCHEDULE"];
const STATUSES: AIStudyPlanStatus[] = ["ACTIVE", "COMPLETED", "ARCHIVED"];

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function inTwoWeeksIso(): string {
  const d = new Date();
  d.setDate(d.getDate() + 14);
  return d.toISOString().slice(0, 10);
}

interface GenerateFormState {
  title: string;
  goal: string;
  startDate: string;
  endDate: string;
  dailyMinutes: string;
}

function emptyGenerateForm(): GenerateFormState {
  return { title: "", goal: "", startDate: todayIso(), endDate: inTwoWeeksIso(), dailyMinutes: "" };
}

export default function StudentStudyPlansPage() {
  const [planTypeFilter, setPlanTypeFilter] = useState<string>(ALL);
  const [statusFilter, setStatusFilter] = useState<string>(ALL);
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null);

  const { data, isLoading, error, setPage, refresh } = useServerTable<
    AIStudyPlanSummaryResponse,
    { planType?: AIStudyPlanType; status?: AIStudyPlanStatus }
  >(
    (params) =>
      aiService.listStudyPlans({
        page: params.page,
        size: params.size,
        planType: params.planType,
        status: params.status,
      }),
    {
      planType: planTypeFilter === ALL ? undefined : (planTypeFilter as AIStudyPlanType),
      status: statusFilter === ALL ? undefined : (statusFilter as AIStudyPlanStatus),
    },
    { pageSize: 10 },
  );

  const [generateForm, setGenerateForm] = useState<GenerateFormState>(emptyGenerateForm());
  const [generating, setGenerating] = useState<"plan" | "revision" | null>(null);
  const [generateError, setGenerateError] = useState<string | null>(null);

  async function handleGenerate(kind: "plan" | "revision") {
    if (!generateForm.startDate || !generateForm.endDate) {
      setGenerateError("Start and end dates are required.");
      return;
    }
    setGenerating(kind);
    setGenerateError(null);
    try {
      const payload = {
        title: generateForm.title.trim() ? generateForm.title.trim() : undefined,
        goal: generateForm.goal.trim() ? generateForm.goal.trim() : undefined,
        startDate: generateForm.startDate,
        endDate: generateForm.endDate,
        dailyMinutes: generateForm.dailyMinutes.trim() ? Number(generateForm.dailyMinutes.trim()) : undefined,
      };
      const created =
        kind === "plan" ? await aiService.generateStudyPlan(payload) : await aiService.generateRevisionSchedule(payload);
      setGenerateForm(emptyGenerateForm());
      refresh();
      setSelectedPlanId(created.id);
    } catch (err) {
      setGenerateError(
        extractErrorMessage(
          err,
          "Failed to generate the plan. The AI assistant may not be configured — check /student/ai.",
        ),
      );
    } finally {
      setGenerating(null);
    }
  }

  if (selectedPlanId !== null) {
    return (
      <div className="space-y-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Study plan</h1>
          <p className="text-muted-foreground">Review, edit, and track this plan's items.</p>
        </div>
        <StudyPlanEditor
          planId={selectedPlanId}
          onClose={() => setSelectedPlanId(null)}
          onChanged={refresh}
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Study plans</h1>
        <p className="text-muted-foreground">
          AI-suggested study plans and revision schedules, grounded in your real marks and attendance.
          Every plan is advisory — review it before you rely on it, and edit anything that doesn't fit.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Generate a plan</CardTitle>
          <CardDescription>Pick a date range; the assistant fills in the daily items.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              void handleGenerate("plan");
            }}
          >
            {generateError && (
              <Alert variant="destructive">
                <AlertDescription>{generateError}</AlertDescription>
              </Alert>
            )}
            <div className="space-y-1.5">
              <Label htmlFor="gen-title">Title</Label>
              <Input
                id="gen-title"
                value={generateForm.title}
                maxLength={150}
                onChange={(event) => setGenerateForm((f) => ({ ...f, title: event.target.value }))}
                placeholder="Optional — the assistant will suggest one"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="gen-goal">Goal</Label>
              <Input
                id="gen-goal"
                value={generateForm.goal}
                maxLength={500}
                onChange={(event) => setGenerateForm((f) => ({ ...f, goal: event.target.value }))}
                placeholder="Optional — e.g. clear the backlog before the semester exam"
              />
            </div>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label htmlFor="gen-start">Start date</Label>
                <Input
                  id="gen-start"
                  type="date"
                  value={generateForm.startDate}
                  onChange={(event) => setGenerateForm((f) => ({ ...f, startDate: event.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="gen-end">End date</Label>
                <Input
                  id="gen-end"
                  type="date"
                  value={generateForm.endDate}
                  onChange={(event) => setGenerateForm((f) => ({ ...f, endDate: event.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="gen-daily">Daily minutes</Label>
                <Input
                  id="gen-daily"
                  type="number"
                  min={15}
                  max={720}
                  value={generateForm.dailyMinutes}
                  onChange={(event) => setGenerateForm((f) => ({ ...f, dailyMinutes: event.target.value }))}
                  placeholder="Optional"
                />
              </div>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={generating !== null}>
                {generating === "plan" ? "Generating…" : "Generate study plan"}
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={generating !== null}
                onClick={() => void handleGenerate("revision")}
              >
                {generating === "revision" ? "Generating…" : "Generate revision schedule"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Your plans</CardTitle>
          <div className="flex flex-wrap gap-2 pt-2">
            <Select value={planTypeFilter} onValueChange={(value) => value && setPlanTypeFilter(value)}>
              <SelectTrigger className="w-56">
                <SelectValue placeholder="All types" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All types</SelectItem>
                {PLAN_TYPES.map((type) => (
                  <SelectItem key={type} value={type}>
                    {type === "REVISION_SCHEDULE" ? "Revision schedule" : "Study plan"}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={statusFilter} onValueChange={(value) => value && setStatusFilter(value)}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="All statuses" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All statuses</SelectItem>
                {STATUSES.map((status) => (
                  <SelectItem key={status} value={status}>
                    {status}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardHeader>
        <CardContent className="space-y-2 px-0">
          {error && (
            <div className="px-4 pb-2">
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            </div>
          )}
          {isLoading && <p className="px-4 py-8 text-center text-muted-foreground">Loading…</p>}
          {!isLoading && data?.content.length === 0 && (
            <p className="px-4 py-8 text-center text-muted-foreground">
              No plans yet — generate one above.
            </p>
          )}
          <div className="space-y-2 px-4">
            {!isLoading &&
              data?.content.map((plan) => (
                <button
                  key={plan.id}
                  type="button"
                  onClick={() => setSelectedPlanId(plan.id)}
                  className="flex w-full flex-col gap-1 rounded-lg border border-border p-3 text-left transition-colors hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">{plan.title}</span>
                      <Badge variant={plan.planType === "REVISION_SCHEDULE" ? "secondary" : "default"}>
                        {plan.planType === "REVISION_SCHEDULE" ? "Revision schedule" : "Study plan"}
                      </Badge>
                      <Badge variant="outline">{plan.status}</Badge>
                      {plan.source === "AI_GENERATED" && <Badge variant="outline">Advisory</Badge>}
                      {plan.edited && <Badge variant="secondary">Edited by you</Badge>}
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {plan.startDate} – {plan.endDate} · {plan.completedItemCount}/{plan.itemCount} items done
                    </p>
                  </div>
                </button>
              ))}
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
    </div>
  );
}
