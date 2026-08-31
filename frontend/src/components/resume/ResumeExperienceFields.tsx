import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { EMPLOYMENT_TYPES, EMPLOYMENT_TYPE_LABELS } from "@/types/resume";
import type { ResumeExperienceRequest } from "@/types/resume";

const NONE_VALUE = "__none__";

export function ResumeExperienceFields({
  value,
  onChange,
  disabled = false,
}: {
  value: ResumeExperienceRequest;
  onChange: (patch: Partial<ResumeExperienceRequest>) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-1.5">
        <Label>Company *</Label>
        <Input
          value={value.companyName}
          maxLength={200}
          disabled={disabled}
          onChange={(e) => onChange({ companyName: e.target.value })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Role title *</Label>
        <Input
          value={value.roleTitle}
          maxLength={150}
          disabled={disabled}
          onChange={(e) => onChange({ roleTitle: e.target.value })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Location</Label>
        <Input
          value={value.location ?? ""}
          maxLength={150}
          disabled={disabled}
          onChange={(e) => onChange({ location: e.target.value || null })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Employment type</Label>
        <Select
          value={value.employmentType ?? NONE_VALUE}
          disabled={disabled}
          onValueChange={(v) =>
            onChange({ employmentType: v === NONE_VALUE ? null : (v as ResumeExperienceRequest["employmentType"]) })
          }
        >
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Not specified" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NONE_VALUE}>Not specified</SelectItem>
            {EMPLOYMENT_TYPES.map((t) => (
              <SelectItem key={t} value={t}>
                {EMPLOYMENT_TYPE_LABELS[t]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label>Start date *</Label>
        <Input
          type="date"
          value={value.startDate}
          disabled={disabled}
          onChange={(e) => onChange({ startDate: e.target.value })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>End date {value.currentPosition ? "" : "*"}</Label>
        <Input
          type="date"
          value={value.endDate ?? ""}
          disabled={disabled || value.currentPosition}
          onChange={(e) => onChange({ endDate: e.target.value || null })}
        />
      </div>
      <label className="flex items-center gap-2 text-sm sm:col-span-2">
        <input
          type="checkbox"
          checked={value.currentPosition}
          disabled={disabled}
          onChange={(e) =>
            onChange({
              currentPosition: e.target.checked,
              endDate: e.target.checked ? null : value.endDate,
            })
          }
          className="size-4 rounded border-input"
        />
        I currently work here
      </label>
      <div className="space-y-1.5 sm:col-span-2">
        <Label>Description</Label>
        <textarea
          className="min-h-20 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          value={value.description ?? ""}
          maxLength={20000}
          disabled={disabled}
          onChange={(e) => onChange({ description: e.target.value || null })}
          placeholder="Key responsibilities and achievements…"
        />
      </div>
    </div>
  );
}
