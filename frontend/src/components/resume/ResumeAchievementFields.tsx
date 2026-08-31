import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ResumeAchievementRequest } from "@/types/resume";

export function ResumeAchievementFields({
  value,
  onChange,
  disabled = false,
}: {
  value: ResumeAchievementRequest;
  onChange: (patch: Partial<ResumeAchievementRequest>) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-1.5">
        <Label>Title *</Label>
        <Input
          value={value.title}
          maxLength={200}
          disabled={disabled}
          onChange={(e) => onChange({ title: e.target.value })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Issuer</Label>
        <Input
          value={value.issuer ?? ""}
          maxLength={200}
          disabled={disabled}
          onChange={(e) => onChange({ issuer: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Date</Label>
        <Input
          type="date"
          value={value.achievedOn ?? ""}
          disabled={disabled}
          onChange={(e) => onChange({ achievedOn: e.target.value || null })}
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
