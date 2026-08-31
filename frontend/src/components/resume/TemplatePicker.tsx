import { CheckIcon } from "lucide-react";

import { cn } from "@/lib/utils";
import { RESUME_TEMPLATES, RESUME_TEMPLATE_LABELS } from "@/types/resume";
import type { ResumeTemplate } from "@/types/resume";

const TEMPLATE_DESCRIPTIONS: Record<ResumeTemplate, string> = {
  CLASSIC: "Traditional single-column layout — safe for any industry.",
  MODERN: "Clean headings with a touch of color for a contemporary look.",
  COMPACT: "Tighter spacing to fit more onto one page.",
};

/** A row of selectable template cards. The PDF itself decides how each template
 *  renders — this only records the student's choice. */
export function TemplatePicker({
  value,
  onChange,
  disabled = false,
}: {
  value: ResumeTemplate;
  onChange: (template: ResumeTemplate) => void;
  disabled?: boolean;
}) {
  return (
    <div className="grid gap-2 sm:grid-cols-3">
      {RESUME_TEMPLATES.map((template) => {
        const selected = template === value;
        return (
          <button
            key={template}
            type="button"
            disabled={disabled}
            onClick={() => onChange(template)}
            className={cn(
              "flex flex-col items-start gap-1 rounded-lg border p-3 text-left text-sm transition-colors disabled:pointer-events-none disabled:opacity-50",
              selected
                ? "border-primary bg-primary/5 ring-1 ring-primary"
                : "border-border hover:bg-muted",
            )}
          >
            <span className="flex w-full items-center justify-between font-medium">
              {RESUME_TEMPLATE_LABELS[template]}
              {selected && <CheckIcon className="size-4 text-primary" />}
            </span>
            <span className="text-xs text-muted-foreground">{TEMPLATE_DESCRIPTIONS[template]}</span>
          </button>
        );
      })}
    </div>
  );
}
