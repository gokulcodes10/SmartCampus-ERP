import type {
  InterviewDifficulty,
  InterviewMode,
  InterviewOutcome,
  InterviewQuestionCategory,
  InterviewQuestionSource,
  InterviewStatus,
  InterviewType,
} from "@/types/interview";

/** Shared label/format/validation helpers for every Phase 10 (Interview) page. */

type BadgeVariant = "default" | "secondary" | "destructive" | "outline";

export const CATEGORY_LABELS: Record<InterviewQuestionCategory, string> = {
  TECHNICAL: "Technical",
  HR: "HR",
  BEHAVIOURAL: "Behavioural",
  CODING: "Coding",
  APTITUDE: "Aptitude",
  COMPANY_SPECIFIC: "Company specific",
};

export const DIFFICULTY_LABELS: Record<InterviewDifficulty, string> = {
  EASY: "Easy",
  MEDIUM: "Medium",
  HARD: "Hard",
};

export const DIFFICULTY_BADGE_VARIANT: Record<InterviewDifficulty, BadgeVariant> = {
  EASY: "secondary",
  MEDIUM: "outline",
  HARD: "destructive",
};

export const SOURCE_LABELS: Record<InterviewQuestionSource, string> = {
  CURATED: "Curated",
  AI_GENERATED: "AI generated",
};

export const INTERVIEW_TYPE_LABELS: Record<InterviewType, string> = {
  TECHNICAL: "Technical",
  HR: "HR",
  BEHAVIOURAL: "Behavioural",
  CODING: "Coding",
  APTITUDE: "Aptitude",
  MANAGERIAL: "Managerial",
  MOCK: "Mock",
};

export const MODE_LABELS: Record<InterviewMode, string> = {
  ONLINE: "Online",
  ONSITE: "Onsite",
  PHONE: "Phone",
};

export const STATUS_LABELS: Record<InterviewStatus, string> = {
  SCHEDULED: "Scheduled",
  RESCHEDULED: "Rescheduled",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
  NO_SHOW: "No-show",
};

export const STATUS_BADGE_VARIANT: Record<InterviewStatus, BadgeVariant> = {
  SCHEDULED: "default",
  RESCHEDULED: "outline",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
  NO_SHOW: "destructive",
};

export const OUTCOME_LABELS: Record<InterviewOutcome, string> = {
  AWAITING_RESULT: "Awaiting result",
  SELECTED: "Selected",
  REJECTED: "Rejected",
  ON_HOLD: "On hold",
};

export const OUTCOME_BADGE_VARIANT: Record<InterviewOutcome, BadgeVariant> = {
  AWAITING_RESULT: "outline",
  SELECTED: "secondary",
  REJECTED: "destructive",
  ON_HOLD: "outline",
};

/**
 * §7 status lifecycle, enforced client-side too so the form never lets a caller
 * attempt a move the backend would reject:
 *   SCHEDULED   -> RESCHEDULED | COMPLETED | CANCELLED | NO_SHOW
 *   RESCHEDULED -> RESCHEDULED | COMPLETED | CANCELLED | NO_SHOW
 *   COMPLETED / CANCELLED / NO_SHOW -> terminal (no further transitions)
 */
export const STATUS_TRANSITIONS: Record<InterviewStatus, InterviewStatus[]> = {
  SCHEDULED: ["RESCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"],
  RESCHEDULED: ["RESCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW"],
  COMPLETED: [],
  CANCELLED: [],
  NO_SHOW: [],
};

/** Converts a backend `LocalDateTime` string to the value a `datetime-local` input needs. */
export function toDatetimeLocalValue(iso: string): string {
  return iso.length >= 16 ? iso.slice(0, 16) : iso;
}

/** Converts a `datetime-local` input value back to a `LocalDateTime`-shaped string. */
export function fromDatetimeLocalValue(value: string): string {
  return value.length === 16 ? `${value}:00` : value;
}

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
}
