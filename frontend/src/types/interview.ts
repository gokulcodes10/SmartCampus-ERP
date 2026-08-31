/**
 * Types for the Phase 10 (Interview) API: the interview question bank (with
 * per-student progress) and interview scheduling. These mirror the PHASE 10
 * IMPLEMENTATION CONTRACT handed down for this wave exactly — field-for-field.
 *
 * Written against the contract before/while the backend for this phase was being
 * built concurrently. Every response type below is **flat**: a referenced entity is
 * always `xId` + `xName` scalars, never a nested `{ id, ... }` object — see the note
 * at the top of `types/academic.ts`, which this file follows exactly.
 *
 * All times are `LocalDateTime` strings like `"2026-09-01T10:00:00"` — no offset, no
 * trailing `Z` — typed as `string` throughout, matching `hibernate.jdbc.time_zone=UTC`
 * server-side serialization.
 *
 * NOT VERIFIED against a live backend response at authoring time — see the build
 * report for exactly what was and was not re-checked once the backend booted.
 */

import type { ListParams, Page } from "@/types/academic";

export type { Page };

// ---------------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------------

export type InterviewQuestionCategory =
  | "TECHNICAL"
  | "HR"
  | "BEHAVIOURAL"
  | "CODING"
  | "APTITUDE"
  | "COMPANY_SPECIFIC";

export type InterviewDifficulty = "EASY" | "MEDIUM" | "HARD";

export type InterviewQuestionSource = "CURATED" | "AI_GENERATED";

export type InterviewType =
  | "TECHNICAL"
  | "HR"
  | "BEHAVIOURAL"
  | "CODING"
  | "APTITUDE"
  | "MANAGERIAL"
  | "MOCK";

export type InterviewMode = "ONLINE" | "ONSITE" | "PHONE";

export type InterviewStatus = "SCHEDULED" | "RESCHEDULED" | "COMPLETED" | "CANCELLED" | "NO_SHOW";

export type InterviewOutcome = "AWAITING_RESULT" | "SELECTED" | "REJECTED" | "ON_HOLD";

// ---------------------------------------------------------------------------------
// Question bank
// ---------------------------------------------------------------------------------

export interface InterviewQuestionResponse {
  id: number;
  category: InterviewQuestionCategory;
  difficulty: InterviewDifficulty;
  question: string;
  answer: string | null;
  explanation: string | null;
  companyName: string | null;
  tags: string | null;
  source: InterviewQuestionSource;
  model: string | null;
  ownerStudentId: number | null;
  mine: boolean;
  completed: boolean;
  bookmarked: boolean;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InterviewQuestionCreateRequest {
  category: InterviewQuestionCategory;
  /** null -> MEDIUM server-side. */
  difficulty?: InterviewDifficulty | null;
  question: string;
  answer?: string | null;
  explanation?: string | null;
  companyName?: string | null;
  tags?: string | null;
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface -- identical component list to InterviewQuestionCreateRequest, per contract.
export interface InterviewQuestionUpdateRequest extends InterviewQuestionCreateRequest {}

/** Both fields nullable on purpose: `null`/omitted means "leave this flag unchanged". */
export interface InterviewQuestionProgressRequest {
  completed?: boolean | null;
  bookmarked?: boolean | null;
}

export interface InterviewCategoryProgressResponse {
  category: InterviewQuestionCategory;
  total: number;
  completed: number;
}

export interface InterviewProgressSummaryResponse {
  totalQuestions: number;
  completed: number;
  bookmarked: number;
  notStarted: number;
  /** One entry per one of the six categories, even when the bank has none (total=0). */
  byCategory: InterviewCategoryProgressResponse[];
}

export interface InterviewQuestionGenerateRequest {
  category: InterviewQuestionCategory;
  /** null -> MEDIUM server-side. */
  difficulty?: InterviewDifficulty | null;
  topic?: string | null;
  companyName?: string | null;
  /** null -> 5 server-side. 1-10. */
  count?: number | null;
}

export interface InterviewGeneratedQuestionsResponse {
  model: string;
  count: number;
  questions: InterviewQuestionResponse[];
}

/**
 * `search` (the shared table hook's field) is translated to the backend's `q` param
 * inside interviewQuestionService.ts — see the query-parameter trap note there.
 */
export interface InterviewQuestionListParams extends ListParams {
  category?: InterviewQuestionCategory;
  difficulty?: InterviewDifficulty;
  companyName?: string;
  source?: InterviewQuestionSource;
  bookmarked?: boolean;
  completed?: boolean;
  mine?: boolean;
}

// ---------------------------------------------------------------------------------
// Scheduling
// ---------------------------------------------------------------------------------

export interface InterviewResponse {
  id: number;
  studentId: number;
  studentName: string;
  studentRegisterNumber: string | null;
  title: string;
  interviewType: InterviewType;
  companyName: string | null;
  roundName: string | null;
  mode: InterviewMode;
  meetingLink: string | null;
  location: string | null;
  interviewerName: string | null;
  scheduledStart: string;
  scheduledEnd: string;
  status: InterviewStatus;
  outcome: InterviewOutcome | null;
  feedback: string | null;
  notes: string | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InterviewScheduleRequest {
  /** ADMIN: required. STUDENT: must be null/omitted or their own id. */
  studentId?: number | null;
  title: string;
  interviewType: InterviewType;
  companyName?: string | null;
  roundName?: string | null;
  mode: InterviewMode;
  meetingLink?: string | null;
  location?: string | null;
  interviewerName?: string | null;
  scheduledStart: string;
  scheduledEnd: string;
  notes?: string | null;
}

/** Deliberately carries NO times and NO status — those have their own endpoints. */
export interface InterviewUpdateRequest {
  title: string;
  interviewType: InterviewType;
  companyName?: string | null;
  roundName?: string | null;
  mode: InterviewMode;
  meetingLink?: string | null;
  location?: string | null;
  interviewerName?: string | null;
  notes?: string | null;
}

export interface InterviewRescheduleRequest {
  scheduledStart: string;
  scheduledEnd: string;
}

export interface InterviewStatusUpdateRequest {
  status: InterviewStatus;
  outcome?: InterviewOutcome | null;
  feedback?: string | null;
  cancellationReason?: string | null;
}

/**
 * `search` (the shared table hook's field) is translated to the backend's `q` param
 * inside interviewService.ts — see the query-parameter trap note there.
 */
export interface InterviewListParams extends ListParams {
  status?: InterviewStatus;
  /** ADMIN only server-side. */
  studentId?: number;
  from?: string;
  to?: string;
}

export type InterviewQuestionPage = Page<InterviewQuestionResponse>;
export type InterviewPage = Page<InterviewResponse>;
