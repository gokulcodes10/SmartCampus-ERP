/**
 * Types for the Phase 6 (AI Assistant) API: conversational chat, grounded feature
 * shortcuts (explain / practice questions / MCQs) and AI-generated study plans /
 * revision schedules. These mirror `smartcampus.dto.*` records described in the Phase 6
 * implementation contract and `db/migration/V6__ai.sql` — do not add or rename a field,
 * the backend DTOs are fixed.
 *
 * NULL POLICY: every percentage/grade/CGPA field is `null` when it cannot be computed
 * from real rows. Render that as "—" / "Not graded" — never 0, never "N/A".
 *
 * The API key never appears anywhere in these shapes (§25, §61) — `AIStatusResponse`
 * deliberately carries no credential, only whether the provider is configured.
 */

import type { ExamResponse } from "@/types/academicOps";

export type AIFeature =
  | "CHAT"
  | "STUDY_PLAN"
  | "TOPIC_EXPLANATION"
  | "PRACTICE_QUESTIONS"
  | "MCQ"
  | "REVISION_SCHEDULE";

export type AIMessageRole = "SYSTEM" | "USER" | "ASSISTANT";

export type AIStudyPlanType = "STUDY_PLAN" | "REVISION_SCHEDULE";

export type AIStudyPlanStatus = "ACTIVE" | "COMPLETED" | "ARCHIVED";

export type AIStudyPlanSource = "AI_GENERATED" | "STUDENT_CREATED";

/** Request vocabulary only — never a persisted value. */
export type AIDifficulty = "EASY" | "MEDIUM" | "HARD";

// ---------------------------------------------------------------------------------
// Requests
// ---------------------------------------------------------------------------------

export interface AIConversationCreateRequest {
  title?: string;
  feature?: AIFeature;
  message: string;
}

export interface AIMessageCreateRequest {
  message: string;
}

export interface AIConversationRenameRequest {
  title: string;
}

export interface AIExplainRequest {
  topic: string;
  focus?: string;
  subjectId?: number;
}

export interface AIPracticeQuestionsRequest {
  topic: string;
  subjectId?: number;
  count?: number;
  difficulty?: AIDifficulty;
}

export interface AIMcqRequest {
  topic: string;
  subjectId?: number;
  count?: number;
  difficulty?: AIDifficulty;
}

/** Used by BOTH POST /study-plans/generate and POST /study-plans/revision-schedule. */
export interface AIStudyPlanGenerateRequest {
  title?: string;
  goal?: string;
  startDate: string;
  endDate: string;
  subjectIds?: number[];
  dailyMinutes?: number;
}

export interface AIStudyPlanUpdateRequest {
  title: string;
  goal?: string;
  startDate: string;
  endDate: string;
  status: AIStudyPlanStatus;
}

/**
 * Used by BOTH add-item and update-item. `completed` and `position` are OPTIONAL —
 * null/omitted means "leave unchanged" on update, or "default" on create.
 */
export interface AIStudyPlanItemRequest {
  subjectId?: number;
  subjectLabel?: string;
  scheduledDate: string;
  title: string;
  description?: string;
  durationMinutes?: number;
  completed?: boolean;
  position?: number;
}

// ---------------------------------------------------------------------------------
// Responses
// ---------------------------------------------------------------------------------

export interface AIConversationResponse {
  id: number;
  title: string;
  feature: AIFeature;
  model: string | null;
  messageCount: number;
  lastMessageAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AIMessageResponse {
  id: number;
  conversationId: number;
  seqNo: number;
  role: AIMessageRole;
  content: string;
  model: string | null;
  grounded: boolean;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  latencyMs: number | null;
  createdAt: string;
}

export interface AIConversationDetailResponse {
  conversation: AIConversationResponse;
  messages: AIMessageResponse[];
}

export interface AIChatTurnResponse {
  conversationId: number;
  userMessage: AIMessageResponse;
  assistantMessage: AIMessageResponse;
}

export interface AIStatusResponse {
  configured: boolean;
  provider: string;
  baseUrl: string;
  model: string | null;
  rateLimitPerMinute: number;
  rateLimitPerDay: number;
  usedLastMinute: number;
  usedToday: number;
  remainingMinute: number;
  remainingDay: number;
}

export interface AIModelResponse {
  id: string;
  ownedBy: string;
  contextWindow: number | null;
}

export interface AIWeakSubjectResponse {
  subjectId: number | null;
  subjectCode: string | null;
  subjectName: string | null;
  marksPercentage: number | null;
  attendancePercentage: number | null;
  reason: string;
}

export interface AISubjectPerformanceResponse {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  credits: number | null;
  academicYear: string | null;
  semester: number | null;
  marksPercentage: number | null;
  grade: string | null;
  attendancePercentage: number | null;
  lowAttendance: boolean;
}

export interface AIStudyPlanItemResponse {
  id: number;
  subjectId: number | null;
  subjectCode: string | null;
  subjectName: string | null;
  subjectLabel: string | null;
  position: number;
  scheduledDate: string;
  title: string;
  description: string | null;
  durationMinutes: number | null;
  completed: boolean;
  completedAt: string | null;
}

export interface AIStudyPlanResponse {
  id: number;
  conversationId: number | null;
  planType: AIStudyPlanType;
  title: string;
  goal: string | null;
  startDate: string;
  endDate: string;
  status: AIStudyPlanStatus;
  source: AIStudyPlanSource;
  model: string | null;
  edited: boolean;
  createdAt: string;
  updatedAt: string;
  items: AIStudyPlanItemResponse[];
}

export interface AIStudyPlanSummaryResponse {
  id: number;
  conversationId: number | null;
  planType: AIStudyPlanType;
  title: string;
  goal: string | null;
  startDate: string;
  endDate: string;
  status: AIStudyPlanStatus;
  source: AIStudyPlanSource;
  model: string | null;
  edited: boolean;
  itemCount: number;
  completedItemCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AIStudentContextResponse {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  departmentName: string | null;
  courseName: string | null;
  currentSemester: number | null;
  section: string | null;
  cgpa: number | null;
  totalGradedCredits: number | null;
  overallAttendancePercentage: number | null;
  minimumAttendancePercentage: number | null;
  lowAttendance: boolean;
  weakSubjects: AIWeakSubjectResponse[];
  subjects: AISubjectPerformanceResponse[];
  upcomingExams: ExamResponse[];
  hasAcademicData: boolean;
}
