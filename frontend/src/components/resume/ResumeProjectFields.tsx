import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ResumeProjectRequest } from "@/types/resume";

export function ResumeProjectFields({
  value,
  onChange,
  disabled = false,
}: {
  value: ResumeProjectRequest;
  onChange: (patch: Partial<ResumeProjectRequest>) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-1.5 sm:col-span-2">
        <Label>Project name *</Label>
        <Input
          value={value.name}
          maxLength={200}
          disabled={disabled}
          onChange={(e) => onChange({ name: e.target.value })}
        />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label>Tech stack</Label>
        <Input
          value={value.techStack ?? ""}
          maxLength={255}
          disabled={disabled}
          onChange={(e) => onChange({ techStack: e.target.value || null })}
          placeholder="e.g. React, Spring Boot, MySQL"
        />
      </div>
      <div className="space-y-1.5">
        <Label>Project URL</Label>
        <Input
          value={value.projectUrl ?? ""}
          maxLength={255}
          disabled={disabled}
          onChange={(e) => onChange({ projectUrl: e.target.value || null })}
          placeholder="https://…"
        />
      </div>
      <div className="space-y-1.5">
        <Label>Repository URL</Label>
        <Input
          value={value.repositoryUrl ?? ""}
          maxLength={255}
          disabled={disabled}
          onChange={(e) => onChange({ repositoryUrl: e.target.value || null })}
          placeholder="https://…"
        />
      </div>
      <div className="space-y-1.5">
        <Label>Start date</Label>
        <Input
          type="date"
          value={value.startDate ?? ""}
          disabled={disabled}
          onChange={(e) => onChange({ startDate: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>End date</Label>
        <Input
          type="date"
          value={value.endDate ?? ""}
          disabled={disabled}
          onChange={(e) => onChange({ endDate: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5 sm:col-span-2">
        <Label>Description</Label>
        <textarea
          className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          value={value.description ?? ""}
          maxLength={20000}
          disabled={disabled}
          onChange={(e) => onChange({ description: e.target.value || null })}
        />
      </div>
    </div>
  );
}
