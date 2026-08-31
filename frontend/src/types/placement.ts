/**
 * Types for the Phase 8 (Placement) API: companies, jobs, eligibility and applications.
 * Transcribed field-by-field from the authoritative Phase 8 implementation contract
 * (backend entities/DTOs in `smartcampus.entity` / `smartcampus.dto`), NOT from
 * intuition about what a job posting "should" look like — see Phase 3's postmortem
 * (AGENT_CONTEXT.md trap #6) for why that distinction matters.
 *
 * Conventions applied throughout:
 *  - Java `BigDecimal` -> `number | null` (Jackson emits a JSON number; every BigDecimal
 *    column in the V8 migration is nullable, so every BigDecimal-backed field here is too).
 *  - Java `Long` -> `number`; primitive `long` (counts) -> `number`, never null.
 *  - `LocalDateTime` -> `string` (ISO-8601, no zone suffix). `LocalDate` -> `string`
 *    ("YYYY-MM-DD").
 *  - Every field the backend DTO does not annotate `@NotNull` / does not derive from a
 *    NOT NULL column is typed `| null` here — reference types are nullable by default in
 *    Java records unless the DDL or validation says otherwise.
 *  - Everything is FLAT (an `xId` + `xName` pair, matching the rest of this codebase's
 *    admin DTOs — see academic.ts) EXCEPT `eligibleDepartments: JobDepartmentRef[]`,
 *    `reasons: EligibilityReason[]` and the analytics list fields, which the contract
 *    explicitly defines as arrays of small records.
 *  - `Page<T>` / `ListParams` are reused from `@/types/academic` — no second pagination
 *    type is defined here.
 *
 * UNVERIFIED against a live backend response: no Phase 8 backend controller exists on
 * disk yet at the time this file was written (only V8__placement.sql). The integrator
 * must diff a real `GET /api/jobs/{id}` / `GET /api/companies/{id}` response against
 * this file before trusting it in production.
 */

// ---------------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------------

export type CompanyStatus = "ACTIVE" | "INACTIVE";

export type JobType = "FULL_TIME" | "PART_TIME" | "INTERNSHIP" | "CONTRACT";

export type JobStatus = "DRAFT" | "OPEN" | "CLOSED" | "CANCELLED";

export type ApplicationStatus =
  | "APPLIED"
  | "UNDER_REVIEW"
  | "SHORTLISTED"
  | "INTERVIEW_SCHEDULED"
  | "SELECTED"
  | "REJECTED"
  | "WITHDRAWN";

/**
 * `smartcampus.dto.EligibilityReasonCode`. The first eight are CRITERION codes (any
 * present => `eligible = false`); the last three are BLOCKER codes (never change
 * `eligible`, only `canApply`). See EligibilityPanel for how this split is rendered.
 */
export type EligibilityReasonCode =
  | "PROFILE_NOT_ACTIVE"
  | "DEPARTMENT_NOT_ELIGIBLE"
  | "GRADUATION_YEAR_UNKNOWN"
  | "GRADUATION_YEAR_MISMATCH"
  | "CGPA_NOT_AVAILABLE"
  | "CGPA_BELOW_MINIMUM"
  | "PERCENTAGE_NOT_AVAILABLE"
  | "PERCENTAGE_BELOW_MINIMUM"
  | "DRIVE_NOT_OPEN"
  | "DEADLINE_PASSED"
  | "ALREADY_APPLIED";

export const ELIGIBILITY_CRITERION_CODES: readonly EligibilityReasonCode[] = [
  "PROFILE_NOT_ACTIVE",
  "DEPARTMENT_NOT_ELIGIBLE",
  "GRADUATION_YEAR_UNKNOWN",
  "GRADUATION_YEAR_MISMATCH",
  "CGPA_NOT_AVAILABLE",
  "CGPA_BELOW_MINIMUM",
  "PERCENTAGE_NOT_AVAILABLE",
  "PERCENTAGE_BELOW_MINIMUM",
];

export const ELIGIBILITY_BLOCKER_CODES: readonly EligibilityReasonCode[] = [
  "DRIVE_NOT_OPEN",
  "DEADLINE_PASSED",
  "ALREADY_APPLIED",
];

// ---------------------------------------------------------------------------------
// Company
// ---------------------------------------------------------------------------------

export interface CompanyCreateRequest {
  name: string;
  industry: string | null;
  website: string | null;
  description: string | null;
  location: string | null;
  contactPerson: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
}

export interface CompanyUpdateRequest {
  name: string;
  industry: string | null;
  website: string | null;
  description: string | null;
  location: string | null;
  contactPerson: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  status: CompanyStatus;
}

export interface CompanyResponse {
  id: number;
  name: string;
  industry: string | null;
  website: string | null;
  description: string | null;
  location: string | null;
  contactPerson: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  status: CompanyStatus;
  jobCount: number;
  openJobCount: number;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------------
// Job
// ---------------------------------------------------------------------------------

export interface JobDepartmentRef {
  id: number;
  name: string;
}

export interface JobCreateRequest {
  companyId: number;
  title: string;
  description: string | null;
  location: string | null;
  jobType: JobType;
  openings: number | null;
  salaryMin: number | null;
  salaryMax: number | null;
  /** null => backend defaults to "INR". */
  salaryCurrency: string | null;
  minCgpa: number | null;
  minMarksPercentage: number | null;
  graduationYear: number | null;
  /** null or [] => open to every department — there is no "all" sentinel row. */
  eligibleDepartmentIds: number[] | null;
  applicationDeadline: string;
  driveDate: string | null;
  /** null => backend defaults to DRAFT; only DRAFT or OPEN accepted on create. */
  status: JobStatus | null;
}

/** Same fields as {@link JobCreateRequest} minus `companyId` and `status` — those are
 *  immutable-by-this-endpoint: company can't be reassigned, and status only changes via
 *  `PATCH /api/jobs/{id}/status`. Never send either field on a PUT (§69 trap #2). */
export interface JobUpdateRequest {
  title: string;
  description: string | null;
  location: string | null;
  jobType: JobType;
  openings: number | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  minCgpa: number | null;
  minMarksPercentage: number | null;
  graduationYear: number | null;
  eligibleDepartmentIds: number[] | null;
  applicationDeadline: string;
  driveDate: string | null;
}

export interface JobStatusUpdateRequest {
  status: JobStatus;
}

export interface JobResponse {
  id: number;
  companyId: number;
  companyName: string;
  title: string;
  description: string | null;
  location: string | null;
  jobType: JobType;
  openings: number | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string;
  minCgpa: number | null;
  minMarksPercentage: number | null;
  graduationYear: number | null;
  /** Empty array == open to every department. */
  eligibleDepartments: JobDepartmentRef[];
  applicationDeadline: string;
  driveDate: string | null;
  status: JobStatus;
  /** status === "OPEN" && now <= applicationDeadline, computed server-side. */
  acceptingApplications: boolean;
  postedById: number;
  postedByName: string;
  applicationCount: number;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------------
// Eligibility (§34)
// ---------------------------------------------------------------------------------

export interface EligibilityReason {
  code: EligibilityReasonCode;
  message: string;
  /** Display string, may be null. */
  requirement: string | null;
  /** Display string, may be null. */
  actual: string | null;
}

export interface JobEligibilityResponse {
  jobId: number;
  jobTitle: string;
  companyId: number;
  companyName: string;
  jobStatus: JobStatus;
  applicationDeadline: string;
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  eligible: boolean;
  canApply: boolean;
  reasons: EligibilityReason[];
  minCgpa: number | null;
  studentCgpa: number | null;
  minMarksPercentage: number | null;
  studentMarksPercentage: number | null;
  requiredGraduationYear: number | null;
  studentGraduationYear: number | null;
  eligibleDepartments: JobDepartmentRef[];
  studentDepartmentId: number | null;
  studentDepartmentName: string | null;
  existingApplicationId: number | null;
  existingApplicationStatus: ApplicationStatus | null;
}

export interface EligibleStudentRow {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  email: string;
  departmentId: number | null;
  departmentName: string | null;
  courseId: number | null;
  courseName: string | null;
  currentSemester: number | null;
  section: string | null;
  graduationYear: number | null;
  cgpa: number | null;
  marksPercentage: number | null;
  eligible: boolean;
  hasApplied: boolean;
  applicationStatus: ApplicationStatus | null;
  reasons: EligibilityReason[];
}

// ---------------------------------------------------------------------------------
// Applications
// ---------------------------------------------------------------------------------

export interface PlacementApplicationCreateRequest {
  jobId: number;
  /** Optional (Phase 9); null = apply with no resume attached. */
  resumeId: number | null;
  coverNote: string | null;
}

/** {@code PATCH /api/applications/{id}/resume} (Phase 9). */
export interface ApplicationResumeUpdateRequest {
  resumeId: number;
}

export interface ApplicationStatusUpdateRequest {
  status: ApplicationStatus;
  decisionNote: string | null;
}

export interface ApplicationBulkStatusRequest {
  applicationIds: number[];
  status: ApplicationStatus;
  decisionNote: string | null;
}

export interface BulkStatusSkip {
  applicationId: number;
  reason: string;
}

export interface ApplicationBulkStatusResponse {
  requested: number;
  updated: number;
  skipped: BulkStatusSkip[];
}

export interface PlacementApplicationResponse {
  id: number;
  jobId: number;
  jobTitle: string;
  companyId: number;
  companyName: string;
  jobStatus: JobStatus;
  applicationDeadline: string;
  driveDate: string | null;
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  studentEmail: string;
  departmentId: number | null;
  departmentName: string | null;
  courseId: number | null;
  courseName: string | null;
  currentSemester: number | null;
  section: string | null;
  status: ApplicationStatus;
  coverNote: string | null;
  /** Phase 9; null when no resume is attached to this application. */
  resumeId: number | null;
  /** Phase 9; null when no resume is attached to this application. */
  resumeTitle: string | null;
  cgpaAtApplication: number | null;
  percentageAtApplication: number | null;
  appliedAt: string;
  statusChangedAt: string | null;
  statusChangedById: number | null;
  statusChangedByName: string | null;
  decisionNote: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Admin-side legal status transitions (§ STATUS TRANSITION TABLE). WITHDRAWN is never
 * a legal admin target — a student sets it themselves via the withdraw endpoint, from
 * any of the four keys listed here. SELECTED / REJECTED / WITHDRAWN are terminal (no
 * entry below). Frontend forms use this to avoid ever offering a transition that is
 * guaranteed to 400 (AGENT_CONTEXT.md trap re: "§69 button that does nothing").
 */
export const ADMIN_APPLICATION_TRANSITIONS: Record<ApplicationStatus, ApplicationStatus[]> = {
  APPLIED: ["UNDER_REVIEW", "SHORTLISTED", "REJECTED"],
  UNDER_REVIEW: ["SHORTLISTED", "REJECTED"],
  SHORTLISTED: ["INTERVIEW_SCHEDULED", "SELECTED", "REJECTED"],
  INTERVIEW_SCHEDULED: ["SELECTED", "REJECTED"],
  SELECTED: [],
  REJECTED: [],
  WITHDRAWN: [],
};

/** Statuses a student may withdraw from (non-terminal, non-WITHDRAWN). */
export const WITHDRAWABLE_APPLICATION_STATUSES: readonly ApplicationStatus[] = [
  "APPLIED",
  "UNDER_REVIEW",
  "SHORTLISTED",
  "INTERVIEW_SCHEDULED",
];

// ---------------------------------------------------------------------------------
// Analytics
// ---------------------------------------------------------------------------------

export interface ApplicationStatusSlice {
  status: ApplicationStatus;
  count: number;
}

export interface CompanyPlacementRow {
  companyId: number;
  companyName: string;
  jobCount: number;
  applicationCount: number;
  selectedCount: number;
}

export interface DepartmentPlacementRow {
  departmentId: number;
  departmentName: string;
  activeStudents: number;
  applicants: number;
  selected: number;
  placementRate: number | null;
}

export interface JobFunnelRow {
  jobId: number;
  jobTitle: string;
  companyName: string;
  applicationCount: number;
  shortlistedCount: number;
  selectedCount: number;
  rejectedCount: number;
}

export interface PlacementAnalyticsResponse {
  totalCompanies: number;
  activeCompanies: number;
  totalJobs: number;
  draftJobs: number;
  openJobs: number;
  closedJobs: number;
  cancelledJobs: number;
  totalApplications: number;
  uniqueApplicants: number;
  selectedStudents: number;
  activeStudents: number;
  /** null when activeStudents === 0 — never render this as 0%. */
  placementRate: number | null;
  statusBreakdown: ApplicationStatusSlice[];
  /** Top 5 by selected, then applications. */
  topCompanies: CompanyPlacementRow[];
  departmentBreakdown: DepartmentPlacementRow[];
  /** Top 10 by applicationCount. */
  jobFunnel: JobFunnelRow[];
}
