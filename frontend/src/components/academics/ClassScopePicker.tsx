import { useEffect, useState } from "react";
import { InboxIcon } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import * as teachingService from "@/services/teachingService";
import type { TeachingClassResponse } from "@/types/academicOps";
import { extractErrorMessage } from "@/utils/apiError";

interface ClassScopePickerProps {
  /** The currently selected class, or null if none is selected yet. */
  value: TeachingClassResponse | null;
  /** Always receives the whole tuple — subjectId, academicYear, semester and section
   *  together — never a loose subject id, because every Phase 4 write needs the full
   *  scope tuple to pass ScopedWriteAuthorizer. */
  onChange: (value: TeachingClassResponse) => void;
  label?: string;
  id?: string;
  className?: string;
  /** Auto-selects the first class once loaded if nothing is selected yet. Default true. */
  autoSelectFirst?: boolean;
}

function classKey(cls: TeachingClassResponse): string {
  return String(cls.assignmentId);
}

function classLabel(cls: TeachingClassResponse): string {
  return `${cls.subjectCode} — ${cls.subjectName} · ${cls.academicYear} Sem ${cls.semester} Sec ${cls.section}`;
}

/**
 * Loads the caller's teaching assignments once (`GET /api/teaching/my-classes`) and lets
 * a faculty member choose one (subject, academicYear, semester, section) tuple. Every
 * faculty screen in Phase 4 — attendance, exams, marks — uses this as the single source
 * of "which classes am I allowed to act on".
 *
 * When the faculty member has not been assigned to any class yet, this renders a clear
 * empty state explaining that, rather than a disabled/broken-looking select (§69).
 */
export function ClassScopePicker({
  value,
  onChange,
  label = "Class",
  id = "class-scope",
  className,
  autoSelectFirst = true,
}: ClassScopePickerProps) {
  const [classes, setClasses] = useState<TeachingClassResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    teachingService
      .listMyClasses()
      .then((result) => {
        if (cancelled) return;
        setClasses(result);
        if (autoSelectFirst && result.length > 0 && !value) {
          onChange(result[0]);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, "Failed to load your classes."));
      });
    return () => {
      cancelled = true;
    };
    // Runs once on mount — this list doesn't change during a page's lifetime.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) {
    return (
      <Alert variant="destructive" className={className}>
        <AlertDescription>{error}</AlertDescription>
      </Alert>
    );
  }

  if (classes === null) {
    return (
      <p className={`text-sm text-muted-foreground ${className ?? ""}`}>Loading your classes…</p>
    );
  }

  if (classes.length === 0) {
    return (
      <Alert className={className}>
        <InboxIcon />
        <AlertDescription>
          You have not been assigned to any classes yet. Ask an administrator to assign you to a
          subject before marking attendance, scheduling exams, or entering marks.
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <div className={`space-y-1.5 ${className ?? ""}`}>
      <Label htmlFor={id}>{label}</Label>
      <Select
        value={value ? classKey(value) : null}
        onValueChange={(key) => {
          if (!key) return;
          const selected = classes.find((cls) => classKey(cls) === key);
          if (selected) onChange(selected);
        }}
      >
        <SelectTrigger id={id} className="w-full">
          <SelectValue placeholder="Select a class" />
        </SelectTrigger>
        <SelectContent>
          {classes.map((cls) => (
            <SelectItem key={classKey(cls)} value={classKey(cls)}>
              {classLabel(cls)} ({cls.enrolledStudentCount} students)
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
