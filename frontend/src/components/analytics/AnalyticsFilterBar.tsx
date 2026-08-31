import { useMemo } from "react";
import { FilterXIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import type { AnalyticsFilterOptionsResponse } from "@/types/analytics";

/**
 * The Phase 5 faculty/admin analytics filter set: course, subject, semester, section,
 * academic year, plus a trend-window months selector (and, when `showDepartment` is
 * set, a department picker for the admin overview). Every option list is sourced from
 * `GET /api/analytics/filters` (or the `departments` prop) — nothing here is a
 * hard-coded year/semester/section list.
 */
export interface AnalyticsFilters {
  departmentId?: number;
  courseId?: number;
  subjectId?: number;
  academicYear?: string;
  semester?: number;
  section?: string;
  months?: number;
}

interface AnalyticsFilterBarProps {
  value: AnalyticsFilters;
  onChange: (next: AnalyticsFilters) => void;
  options: AnalyticsFilterOptionsResponse | null;
  loading?: boolean;
  showDepartment?: boolean;
  departments?: { id: number; name: string }[];
  className?: string;
}

/** Sentinel select value meaning "no filter" — an actual empty string is rejected by
 *  the underlying Select primitive and would be ambiguous with "not loaded yet". */
const ALL = "__all__";

/** The trend window is a numeric parameter, not a domain list the backend enumerates —
 *  the server clamps it to `smartcampus.analytics.max-trend-months` regardless of
 *  what is offered here. */
const MONTH_PRESETS = [3, 6, 12, 24];

interface FieldItem {
  value: string;
  label: string;
}

function FilterSkeletonField() {
  return (
    <div className="space-y-1.5">
      <div className="h-3.5 w-16 animate-pulse rounded bg-muted" />
      <div className="h-8 w-full animate-pulse rounded-lg bg-muted" />
    </div>
  );
}

function FilterField({
  id,
  label,
  value,
  onValueChange,
  items,
  disabled,
  emptyMessage,
  placeholder = "All",
}: {
  id: string;
  label: string;
  value: string | undefined;
  onValueChange: (value: string | undefined) => void;
  items: FieldItem[];
  disabled: boolean;
  emptyMessage: string;
  placeholder?: string;
}) {
  if (!disabled && items.length === 0) {
    return (
      <div className="space-y-1.5">
        <Label>{label}</Label>
        <p className="flex h-8 items-center text-sm text-muted-foreground">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Select
        value={disabled ? null : (value ?? ALL)}
        onValueChange={(next) => {
          if (!next) return;
          onValueChange(next === ALL ? undefined : next);
        }}
        disabled={disabled}
      >
        <SelectTrigger id={id} className="w-full">
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>{placeholder}</SelectItem>
          {items.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

export function AnalyticsFilterBar({
  value,
  onChange,
  options,
  loading = false,
  showDepartment = false,
  departments = [],
  className,
}: AnalyticsFilterBarProps) {
  const disabled = loading || options === null;

  const subjectItems = useMemo<FieldItem[]>(() => {
    if (!options) return [];
    const scoped =
      value.courseId == null
        ? options.subjects
        : options.subjects.filter((subject) => subject.courseId === value.courseId);
    return scoped.map((subject) => ({
      value: String(subject.id),
      label: `${subject.code} — ${subject.name}`,
    }));
  }, [options, value.courseId]);

  const courseItems = useMemo<FieldItem[]>(
    () => (options ? options.courses.map((course) => ({ value: String(course.id), label: `${course.code} — ${course.name}` })) : []),
    [options],
  );
  const academicYearItems = useMemo<FieldItem[]>(
    () => (options ? options.academicYears.map((year) => ({ value: year, label: year })) : []),
    [options],
  );
  const semesterItems = useMemo<FieldItem[]>(
    () => (options ? options.semesters.map((sem) => ({ value: String(sem), label: `Semester ${sem}` })) : []),
    [options],
  );
  const sectionItems = useMemo<FieldItem[]>(
    () => (options ? options.sections.map((section) => ({ value: section, label: `Section ${section}` })) : []),
    [options],
  );
  const departmentItems = useMemo<FieldItem[]>(
    () => departments.map((dept) => ({ value: String(dept.id), label: dept.name })),
    [departments],
  );
  const monthItems = useMemo<FieldItem[]>(
    () => MONTH_PRESETS.map((m) => ({ value: String(m), label: `${m} months` })),
    [],
  );

  function patch(next: Partial<AnalyticsFilters>) {
    onChange({ ...value, ...next });
  }

  const hasActiveFilter = Object.values(value).some((v) => v !== undefined);

  if (options === null) {
    return (
      <div className={cn("grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-7", className)}>
        {Array.from({ length: showDepartment ? 7 : 6 }).map((_, i) => (
          <FilterSkeletonField key={i} />
        ))}
      </div>
    );
  }

  return (
    <div className={cn("space-y-2", className)}>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-7">
        {showDepartment && (
          <FilterField
            id="analytics-filter-department"
            label="Department"
            value={value.departmentId != null ? String(value.departmentId) : undefined}
            onValueChange={(v) => patch({ departmentId: v ? Number(v) : undefined })}
            items={departmentItems}
            disabled={disabled}
            emptyMessage="No departments available."
          />
        )}
        <FilterField
          id="analytics-filter-course"
          label="Course"
          value={value.courseId != null ? String(value.courseId) : undefined}
          onValueChange={(v) => patch({ courseId: v ? Number(v) : undefined, subjectId: undefined })}
          items={courseItems}
          disabled={disabled}
          emptyMessage="No courses available."
        />
        <FilterField
          id="analytics-filter-subject"
          label="Subject"
          value={value.subjectId != null ? String(value.subjectId) : undefined}
          onValueChange={(v) => patch({ subjectId: v ? Number(v) : undefined })}
          items={subjectItems}
          disabled={disabled}
          emptyMessage={value.courseId != null ? "No subjects for this course." : "No subjects available."}
        />
        <FilterField
          id="analytics-filter-year"
          label="Academic year"
          value={value.academicYear ?? undefined}
          onValueChange={(v) => patch({ academicYear: v })}
          items={academicYearItems}
          disabled={disabled}
          emptyMessage="No academic years recorded yet."
        />
        <FilterField
          id="analytics-filter-semester"
          label="Semester"
          value={value.semester != null ? String(value.semester) : undefined}
          onValueChange={(v) => patch({ semester: v ? Number(v) : undefined })}
          items={semesterItems}
          disabled={disabled}
          emptyMessage="No semesters recorded yet."
        />
        <FilterField
          id="analytics-filter-section"
          label="Section"
          value={value.section ?? undefined}
          onValueChange={(v) => patch({ section: v })}
          items={sectionItems}
          disabled={disabled}
          emptyMessage="No sections recorded yet."
        />
        <FilterField
          id="analytics-filter-months"
          label="Trend window"
          value={value.months != null ? String(value.months) : undefined}
          onValueChange={(v) => patch({ months: v ? Number(v) : undefined })}
          items={monthItems}
          disabled={disabled}
          placeholder="Default"
          emptyMessage="—"
        />
      </div>
      {hasActiveFilter && (
        <div className="flex justify-end">
          <Button type="button" variant="ghost" size="sm" onClick={() => onChange({})}>
            <FilterXIcon />
            Clear filters
          </Button>
        </div>
      )}
    </div>
  );
}
