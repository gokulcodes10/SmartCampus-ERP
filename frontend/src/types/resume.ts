/**
 * Types for the Phase 9 (Resume) API. Transcribed field-by-field from the
 * authoritative Phase 9 implementation contract (backend entities/DTOs in
 * `smartcampus.entity` / `smartcampus.dto`), not from intuition about what a resume
 * "should" look like — see AGENT_CONTEXT.md trap #6 for why that distinction matters.
 *
 * Conventions applied throughout (matching types/placement.ts and types/academic.ts):
 *  - `LocalDate` -> `string` ("YYYY-MM-DD"). `LocalDateTime` -> `string` (ISO-8601, no
 *    zone suffix).
 *  - Java `BigDecimal` -> `number | null`.
 *  - Every nullable backend field is `T | null` here, never `T | undefined`.
 *  - `display_order` is NOT a request field anywhere — the backend assigns it from the
 *    index of the element in its array. Never add a `displayOrder` field to a *Request
 *    type, and never send one.
 *
 * UNVERIFIED against a live backend response at authoring time (the Phase 9 backend
 * agents were working concurrently and had not yet landed their controllers). Diff a
 * real `POST /api/resumes` response against this file before trusting it in production
 * — see this file's owning task report for exactly what was and wasn't exercised.
 */

import type { Page } from "@/types/academic";

export type { Page };

// ---------------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------------

export type ResumeTemplate = "CLASSIC" | "MODERN" | "COMPACT";
export type GradeScale = "CGPA" | "PERCENTAGE";
export type EmploymentType = "INTERNSHIP" | "FULL_TIME" | "PART_TIME" | "FREELANCE" | "VOLUNTEER";
export type SkillCategory = "TECHNICAL" | "TOOL" | "LANGUAGE" | "SOFT";
export type SkillProficiency = "BEGINNER" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";

// ---------------------------------------------------------------------------------
// Section requests (also doubles as the frontend's per-row form state — the array
// order IS display_order, so these carry no displayOrder field of their own)
// ---------------------------------------------------------------------------------

export interface ResumeEducationRequest {
  institution: string;
  degree: string | null;
  fieldOfStudy: string | null;
  startYear: number | null;
  endYear: number | null;
  gradeValue: number | null;
  gradeScale: GradeScale | null;
}

export interface ResumeExperienceRequest {
  companyName: string;
  roleTitle: string;
  location: string | null;
  employmentType: EmploymentType | null;
  startDate: string;
  endDate: string | null;
  currentPosition: boolean;
  description: string | null;
}

export interface ResumeProjectRequest {
  name: string;
  description: string | null;
  techStack: string | null;
  projectUrl: string | null;
  repositoryUrl: string | null;
  startDate: string | null;
  endDate: string | null;
}

export interface ResumeCertificationRequest {
  name: string;
  issuer: string | null;
  issueDate: string | null;
  expiryDate: string | null;
  credentialId: string | null;
  credentialUrl: string | null;
}

export interface ResumeSkillRequest {
  name: string;
  category: SkillCategory;
  proficiency: SkillProficiency | null;
}

export interface ResumeAchievementRequest {
  title: string;
  description: string | null;
  issuer: string | null;
  achievedOn: string | null;
}

// ---------------------------------------------------------------------------------
// Resume requests
// ---------------------------------------------------------------------------------

/** Used by BOTH `POST /api/resumes` and `PUT /api/resumes/{id}`. */
export interface ResumeSaveRequest {
  title: string;
  template: ResumeTemplate;
  fullName: string;
  email: string;
  phone: string | null;
  location: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  summary: string | null;
  educations: ResumeEducationRequest[];
  experiences: ResumeExperienceRequest[];
  projects: ResumeProjectRequest[];
  certifications: ResumeCertificationRequest[];
  skills: ResumeSkillRequest[];
  achievements: ResumeAchievementRequest[];
}

export interface ResumeDuplicateRequest {
  title: string;
}

// ---------------------------------------------------------------------------------
// Section responses (= matching Request fields plus `id` and `displayOrder`)
// ---------------------------------------------------------------------------------

export interface ResumeEducationResponse extends ResumeEducationRequest {
  id: number;
  displayOrder: number;
}

export interface ResumeExperienceResponse extends ResumeExperienceRequest {
  id: number;
  displayOrder: number;
}

export interface ResumeProjectResponse extends ResumeProjectRequest {
  id: number;
  displayOrder: number;
}

export interface ResumeCertificationResponse extends ResumeCertificationRequest {
  id: number;
  displayOrder: number;
}

export interface ResumeSkillResponse extends ResumeSkillRequest {
  id: number;
  displayOrder: number;
}

export interface ResumeAchievementResponse extends ResumeAchievementRequest {
  id: number;
  displayOrder: number;
}

// ---------------------------------------------------------------------------------
// Resume responses
// ---------------------------------------------------------------------------------

export interface ResumeResponse {
  id: number;
  studentId: number;
  title: string;
  template: ResumeTemplate;
  fullName: string;
  email: string;
  phone: string | null;
  location: string | null;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  summary: string | null;
  /** `locked === (lockedAt !== null)`. */
  locked: boolean;
  lockedAt: string | null;
  educations: ResumeEducationResponse[];
  experiences: ResumeExperienceResponse[];
  projects: ResumeProjectResponse[];
  certifications: ResumeCertificationResponse[];
  skills: ResumeSkillResponse[];
  achievements: ResumeAchievementResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface ResumeSummaryResponse {
  id: number;
  title: string;
  template: ResumeTemplate;
  locked: boolean;
  lockedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * `phone` and `location` are ALWAYS null — no source exists for either. `educations`
 * has 0 or 1 element, and when present its `institution` is `""` (this application
 * stores no college name) — leave it blank for the student to fill in, never invent one.
 */
export interface ResumePrefillResponse {
  suggestedTitle: string;
  fullName: string;
  email: string;
  phone: string | null;
  location: string | null;
  educations: ResumeEducationRequest[];
}

// ---------------------------------------------------------------------------------
// Display helpers
// ---------------------------------------------------------------------------------

export const RESUME_TEMPLATES: readonly ResumeTemplate[] = ["CLASSIC", "MODERN", "COMPACT"];

export const RESUME_TEMPLATE_LABELS: Record<ResumeTemplate, string> = {
  CLASSIC: "Classic",
  MODERN: "Modern",
  COMPACT: "Compact",
};

export const GRADE_SCALES: readonly GradeScale[] = ["CGPA", "PERCENTAGE"];

export const GRADE_SCALE_LABELS: Record<GradeScale, string> = {
  CGPA: "CGPA",
  PERCENTAGE: "Percentage",
};

export const EMPLOYMENT_TYPES: readonly EmploymentType[] = [
  "INTERNSHIP",
  "FULL_TIME",
  "PART_TIME",
  "FREELANCE",
  "VOLUNTEER",
];

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  INTERNSHIP: "Internship",
  FULL_TIME: "Full-time",
  PART_TIME: "Part-time",
  FREELANCE: "Freelance",
  VOLUNTEER: "Volunteer",
};

export const SKILL_CATEGORIES: readonly SkillCategory[] = ["TECHNICAL", "TOOL", "LANGUAGE", "SOFT"];

export const SKILL_CATEGORY_LABELS: Record<SkillCategory, string> = {
  TECHNICAL: "Technical",
  TOOL: "Tool",
  LANGUAGE: "Language",
  SOFT: "Soft skill",
};

export const SKILL_PROFICIENCIES: readonly SkillProficiency[] = [
  "BEGINNER",
  "INTERMEDIATE",
  "ADVANCED",
  "EXPERT",
];

export const SKILL_PROFICIENCY_LABELS: Record<SkillProficiency, string> = {
  BEGINNER: "Beginner",
  INTERMEDIATE: "Intermediate",
  ADVANCED: "Advanced",
  EXPERT: "Expert",
};
