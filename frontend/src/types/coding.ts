/**
 * Types for the Phase 7 (Coding) API: problems, test cases, the playground/judge
 * layer, submissions, contests and leaderboards. These mirror the PHASE 7
 * IMPLEMENTATION CONTRACT handed down for this wave (§8 DTOs, §9 endpoints) exactly —
 * field-for-field, one-for-one.
 *
 * Written against the contract before the backend exists (concurrent build). Every
 * response type below is **flat**: a referenced entity is always `xId` + `xName`/
 * `xTitle` scalars, never a nested `{ id, ... }` object — see the note at the top of
 * `types/academic.ts`, which this file follows exactly. Child *collections* (sample
 * cases, test results, contest problems) are arrays of records; that is expected and
 * different from a nested single-entity reference.
 *
 * NOT VERIFIED against a live backend response — the backend for this phase is being
 * written concurrently. See the build report for exactly what to re-check once it boots.
 */

import type { ListParams, Page } from "@/types/academic";

export type { Page, ListParams };

// ---------------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------------

export type ProgrammingLanguage = "JAVA" | "CPP";

export type ProblemDifficulty = "EASY" | "MEDIUM" | "HARD";

/** The full §29 status set, 9 constants. PENDING/RUNNING are non-terminal. */
export type SubmissionStatus =
  | "PENDING"
  | "RUNNING"
  | "ACCEPTED"
  | "WRONG_ANSWER"
  | "TIME_LIMIT_EXCEEDED"
  | "MEMORY_LIMIT_EXCEEDED"
  | "COMPILATION_ERROR"
  | "RUNTIME_ERROR"
  | "INTERNAL_ERROR";

/** The AUTHORING lifecycle of a contest (admin-controlled). */
export type ContestStatus = "DRAFT" | "PUBLISHED" | "CANCELLED";

/** Time-derived, never persisted: now vs. [startTime, endTime]. */
export type ContestPhase = "UPCOMING" | "RUNNING" | "ENDED";

// ---------------------------------------------------------------------------------
// Problems
// ---------------------------------------------------------------------------------

export interface ProblemSummaryResponse {
  id: number;
  slug: string;
  title: string;
  difficulty: ProblemDifficulty;
  timeLimitMs: number;
  memoryLimitKb: number;
  tags: string[];
  published: boolean;
  sampleTestCaseCount: number;
  hiddenTestCaseCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface SampleTestCaseResponse {
  id: number;
  ordinal: number;
  input: string;
  expectedOutput: string;
}

export interface ProblemDetailResponse {
  id: number;
  slug: string;
  title: string;
  description: string;
  inputFormat: string | null;
  outputFormat: string | null;
  constraintsText: string | null;
  sampleInput: string | null;
  sampleOutput: string | null;
  difficulty: ProblemDifficulty;
  timeLimitMs: number;
  memoryLimitKb: number;
  tags: string[];
  published: boolean;
  createdById: number;
  createdByName: string;
  /** SAMPLE cases only, for every caller including ADMIN. */
  sampleTestCases: SampleTestCaseResponse[];
  hiddenTestCaseCount: number;
  createdAt: string;
  updatedAt: string;
}

/** `GET /api/problems/{id}/test-cases` — ADMIN only. Carries hidden inputs/outputs too. */
export interface TestCaseResponse {
  id: number;
  problemId: number;
  ordinal: number;
  input: string;
  expectedOutput: string;
  isSample: boolean;
  weight: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProblemCreateRequest {
  slug: string;
  title: string;
  description: string;
  inputFormat: string | null;
  outputFormat: string | null;
  constraintsText: string | null;
  sampleInput: string | null;
  sampleOutput: string | null;
  difficulty: ProblemDifficulty;
  timeLimitMs: number;
  memoryLimitKb: number;
  /** Joined with "," into the `tags` column server-side; max 255 chars once joined. */
  tags: string[];
  /** null -> false server-side. */
  published: boolean | null;
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface -- identical component list to ProblemCreateRequest, per contract.
export interface ProblemUpdateRequest extends ProblemCreateRequest {}

export interface TestCaseRequest {
  ordinal: number;
  /** May be "" but never null/undefined. */
  input: string;
  /** May be "" but never null/undefined. */
  expectedOutput: string;
  isSample: boolean;
  weight: number;
}

export interface ProblemListParams extends ListParams {
  difficulty?: ProblemDifficulty;
  tag?: string;
  /** Honoured for ADMIN only; forced to true for everyone else server-side. */
  published?: boolean;
}

// ---------------------------------------------------------------------------------
// Playground / execution
// ---------------------------------------------------------------------------------

export interface LanguageResponse {
  language: ProgrammingLanguage;
  label: string;
  judge0LanguageId: number;
  monacoLanguageId: string;
  defaultTemplate: string;
}

export interface RunRequest {
  language: ProgrammingLanguage;
  sourceCode: string;
  stdin?: string;
}

export interface RunResponse {
  status: SubmissionStatus;
  judge0StatusId: number | null;
  judge0StatusDescription: string | null;
  stdout: string | null;
  stderr: string | null;
  compileOutput: string | null;
  message: string | null;
  executionTimeMs: number | null;
  memoryKb: number | null;
}

export interface SampleRunCaseResponse {
  ordinal: number;
  input: string;
  expectedOutput: string;
  actualOutput: string | null;
  stderr: string | null;
  status: SubmissionStatus;
  passed: boolean;
  executionTimeMs: number | null;
  memoryKb: number | null;
}

export interface SampleRunResponse {
  cases: SampleRunCaseResponse[];
  allPassed: boolean;
}

// ---------------------------------------------------------------------------------
// Submissions
// ---------------------------------------------------------------------------------

export interface SubmissionCreateRequest {
  problemId: number;
  language: ProgrammingLanguage;
  sourceCode: string;
  contestId?: number | null;
}

export interface SubmissionSummaryResponse {
  id: number;
  problemId: number;
  problemTitle: string;
  problemDifficulty: ProblemDifficulty;
  studentId: number;
  studentName: string;
  registerNumber: string | null;
  contestId: number | null;
  contestTitle: string | null;
  language: ProgrammingLanguage;
  status: SubmissionStatus;
  passedTestCases: number;
  totalTestCases: number;
  score: number;
  maxScore: number;
  executionTimeMs: number | null;
  memoryKb: number | null;
  failedTestCaseOrdinal: number | null;
  /** Mapped from `entity.createdAt` — there is no separate `submitted_at` column. */
  submittedAt: string;
}

export interface SubmissionTestResultResponse {
  ordinal: number;
  isSample: boolean;
  status: SubmissionStatus;
  passed: boolean;
  executionTimeMs: number | null;
  memoryKb: number | null;
  /** null for a HIDDEN case and a non-ADMIN caller; ADMIN sees everything. */
  input: string | null;
  expectedOutput: string | null;
  actualOutput: string | null;
  stderrOutput: string | null;
}

export interface SubmissionDetailResponse extends SubmissionSummaryResponse {
  sourceCode: string;
  compileOutput: string | null;
  errorMessage: string | null;
  judgedAt: string | null;
  testResults: SubmissionTestResultResponse[];
}

export interface SubmissionListParams extends ListParams {
  problemId?: number;
  contestId?: number;
  status?: SubmissionStatus;
  /** STUDENT callers have this silently replaced with their own id server-side. */
  studentId?: number;
}

export interface CodingStatsResponse {
  totalSubmissions: number;
  acceptedSubmissions: number;
  problemsAttempted: number;
  problemsSolved: number;
  solvedEasy: number;
  solvedMedium: number;
  solvedHard: number;
}

// ---------------------------------------------------------------------------------
// Contests
// ---------------------------------------------------------------------------------

export interface ContestSummaryResponse {
  id: number;
  slug: string;
  title: string;
  startTime: string;
  endTime: string;
  status: ContestStatus;
  phase: ContestPhase;
  penaltyMinutesPerWrongAttempt: number;
  problemCount: number;
  participantCount: number;
  /** True only when the CALLER is a student registered for it; false for ADMIN/FACULTY. */
  registered: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ContestProblemResponse {
  id: number;
  contestId: number;
  problemId: number;
  problemTitle: string;
  difficulty: ProblemDifficulty;
  ordinal: number;
  /** 1->"A", 2->"B", ...; beyond 26 the ordinal itself as a string. */
  label: string;
  points: number;
  /** null/0 for a non-student caller. */
  myBestStatus: SubmissionStatus | null;
  myAttempts: number;
}

export interface ContestDetailResponse extends ContestSummaryResponse {
  description: string | null;
  createdById: number;
  createdByName: string;
  /** False (and `problems` empty) for a non-admin caller while the contest is UPCOMING. */
  problemsVisible: boolean;
  problems: ContestProblemResponse[];
}

export interface ContestCreateRequest {
  slug: string;
  title: string;
  description: string | null;
  startTime: string;
  endTime: string;
  status: ContestStatus;
  penaltyMinutesPerWrongAttempt: number;
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface -- identical component list to ContestCreateRequest, per contract.
export interface ContestUpdateRequest extends ContestCreateRequest {}

export interface ContestProblemRequest {
  problemId: number;
  ordinal: number;
  points: number;
}

export interface ContestParticipantResponse {
  id: number;
  contestId: number;
  studentId: number;
  studentName: string;
  registerNumber: string | null;
  registeredAt: string;
  totalScore: number;
  problemsSolved: number;
  penaltySeconds: number;
  lastAcceptedAt: string | null;
}

export interface ContestLeaderboardRowResponse {
  rank: number;
  studentId: number;
  studentName: string;
  registerNumber: string | null;
  departmentName: string | null;
  totalScore: number;
  problemsSolved: number;
  penaltySeconds: number;
  lastAcceptedAt: string | null;
}

export interface ContestListParams extends ListParams {
  /** ADMIN only server-side. */
  status?: ContestStatus;
  phase?: ContestPhase;
}

// ---------------------------------------------------------------------------------
// Leaderboard
// ---------------------------------------------------------------------------------

export interface GlobalLeaderboardRowResponse {
  rank: number;
  studentId: number;
  studentName: string;
  registerNumber: string | null;
  departmentName: string | null;
  problemsSolved: number;
  totalScore: number;
  lastAcceptedAt: string | null;
}

export interface LeaderboardListParams {
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------------
// Re-exported for callers that only need the page envelope alongside these types.
// ---------------------------------------------------------------------------------

export type ProblemPage = Page<ProblemSummaryResponse>;
export type SubmissionPage = Page<SubmissionSummaryResponse>;
export type ContestPage = Page<ContestSummaryResponse>;
export type ContestLeaderboardPage = Page<ContestLeaderboardRowResponse>;
export type GlobalLeaderboardPage = Page<GlobalLeaderboardRowResponse>;
