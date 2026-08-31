import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { SKILL_CATEGORIES, SKILL_CATEGORY_LABELS, SKILL_PROFICIENCIES, SKILL_PROFICIENCY_LABELS } from "@/types/resume";
import type { ResumeSkillRequest } from "@/types/resume";

const NONE_VALUE = "__none__";

export function ResumeSkillFields({
  value,
  onChange,
  disabled = false,
}: {
  value: ResumeSkillRequest;
  onChange: (patch: Partial<ResumeSkillRequest>) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      <div className="space-y-1.5">
        <Label>Skill name *</Label>
        <Input
          value={value.name}
          maxLength={100}
          disabled={disabled}
          onChange={(e) => onChange({ name: e.target.value })}
        />
      </div>
      <div className="space-y-1.5">
        <Label>Category *</Label>
        <Select
          value={value.category}
          disabled={disabled}
          onValueChange={(v) => onChange({ category: v as ResumeSkillRequest["category"] })}
        >
          <SelectTrigger className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {SKILL_CATEGORIES.map((c) => (
              <SelectItem key={c} value={c}>
                {SKILL_CATEGORY_LABELS[c]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="space-y-1.5">
        <Label>Proficiency</Label>
        <Select
          value={value.proficiency ?? NONE_VALUE}
          disabled={disabled}
          onValueChange={(v) =>
            onChange({ proficiency: v === NONE_VALUE ? null : (v as ResumeSkillRequest["proficiency"]) })
          }
        >
          <SelectTrigger className="w-full">
            <SelectValue placeholder="Not specified" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NONE_VALUE}>Not specified</SelectItem>
            {SKILL_PROFICIENCIES.map((p) => (
              <SelectItem key={p} value={p}>
                {SKILL_PROFICIENCY_LABELS[p]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
