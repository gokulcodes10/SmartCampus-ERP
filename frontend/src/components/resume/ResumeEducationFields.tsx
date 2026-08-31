import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { GRADE_SCALES, GRADE_SCALE_LABELS } from "@/types/resume";
import type { ResumeEducationRequest } from "@/types/resume";

const NONE_VALUE = "__none__";

function toNumberOrNull(raw: string): number | null {
  return raw === "" ? null : Number(raw);
}

export function ResumeEducationFields({
  value,
  onChange,
  disabled = false,
}: {
  value: ResumeEducationRequest;
  onChange: (patch: Partial<ResumeEducationRequest>) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <div className="space-y-1.5 sm:col-span-2">
        <Label>Institution *</Label>
        <Input
          value={value.institution}
          maxLength={200}
          disabled={disabled}
          onChange={(e) => onChange({ institution: e.target.value })}
          placeholder="Name of college / university"
        />
      </div>
      <div className="space-y-1.5">
        <Label>Degree</Label>
        <Input
          value={value.degree ?? ""}
          maxLength={150}
          disabled={disabled}
          onChange={(e) => onChange({ degree: e.target.value || null })}
          placeholder="e.g. B.Tech"
        />
      </div>
      <div className="space-y-1.5">
        <Label>Field of study</Label>
        <Input
          value={value.fieldOfStudy ?? ""}
          maxLength={150}
          disabled={disabled}
          onChange={(e) => onChange({ fieldOfStudy: e.target.value || null })}
          placeholder="e.g. Computer Science"
        />
      </div>
      <div className="space-y-1.5">
        <Label>Start year</Label>
        <Input
          type="number"
          value={value.startYear ?? ""}
          min={1950}
          max={2100}
          disabled={disabled}
          onChange={(e) => onChange({ startYear: toNumberOrNull(e.target.value) })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>End year</Label>
        <Input
          type="number"
          value={value.endYear ?? ""}
          min={1950}
          max={2100}
          disabled={disabled}
          onChange={(e) => onChange({ endYear: toNumberOrNull(e.target.value) })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Grade scale</Label>
        <Select
          value={value.gradeScale ?? NONE_VALUE}
          disabled={disabled}
          onValueChange={(scale) =>
            onChange(
              scale === NONE_VALUE
                ? { gradeScale: null, gradeValue: null }
                : { gradeScale: scale as ResumeEducationRequest["gradeScale"] },
            )
          }
        >
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Not specified" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NONE_VALUE}>Not specified</SelectItem>
            {GRADE_SCALES.map((scale) => (
              <SelectItem key={scale} value={scale}>
                {GRADE_SCALE_LABELS[scale]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label>Grade value {value.gradeScale === "CGPA" ? "(0–10)" : value.gradeScale === "PERCENTAGE" ? "(0–100)" : ""}</Label>
        <Input
          type="number"
          step="0.01"
          value={value.gradeValue ?? ""}
          disabled={disabled || value.gradeScale === null}
          onChange={(e) => onChange({ gradeValue: toNumberOrNull(e.target.value) })}
          placeholder={value.gradeScale === null ? "Select a grade scale first" : undefined}
        />
      </div>
    </div>
  );
}
