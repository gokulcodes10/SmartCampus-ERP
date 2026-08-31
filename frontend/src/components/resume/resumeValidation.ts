import type { ResumeSaveRequest } from "@/types/resume";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Mirrors the backend's `@Valid` and DB `CHECK` rules (see the Phase 9 contract, §C/§E)
 * so the student sees a message before the round trip rather than a raw 400. This is a
 * mirror, not a replacement — the backend re-validates and its message is always
 * surfaced verbatim on top of whatever this catches first.
 */
export function validateResume(form: ResumeSaveRequest): string[] {
  const errors: string[] = [];

  if (!form.title.trim()) errors.push("Resume title is required.");
  if (!form.fullName.trim()) errors.push("Full name is required.");
  if (!form.email.trim()) {
    errors.push("Email is required.");
  } else if (!EMAIL_RE.test(form.email.trim())) {
    errors.push("Email must be a valid email address.");
  }

  form.educations.forEach((edu, i) => {
    const label = `Education #${i + 1}`;
    if (!edu.institution.trim()) errors.push(`${label}: institution is required.`);
    if (edu.startYear !== null && (edu.startYear < 1950 || edu.startYear > 2100)) {
      errors.push(`${label}: start year must be between 1950 and 2100.`);
    }
    if (edu.endYear !== null && (edu.endYear < 1950 || edu.endYear > 2100)) {
      errors.push(`${label}: end year must be between 1950 and 2100.`);
    }
    if (edu.startYear !== null && edu.endYear !== null && edu.endYear < edu.startYear) {
      errors.push(`${label}: end year cannot be before start year.`);
    }
    const hasValue = edu.gradeValue !== null;
    const hasScale = edu.gradeScale !== null;
    if (hasValue !== hasScale) {
      errors.push(`${label}: grade value and grade scale must be supplied together, or left both blank.`);
    } else if (hasValue && hasScale) {
      if (edu.gradeScale === "CGPA" && (edu.gradeValue! < 0 || edu.gradeValue! > 10)) {
        errors.push(`${label}: CGPA must be between 0 and 10.`);
      }
      if (edu.gradeScale === "PERCENTAGE" && (edu.gradeValue! < 0 || edu.gradeValue! > 100)) {
        errors.push(`${label}: percentage must be between 0 and 100.`);
      }
    }
  });

  form.experiences.forEach((exp, i) => {
    const label = `Experience #${i + 1}`;
    if (!exp.companyName.trim()) errors.push(`${label}: company is required.`);
    if (!exp.roleTitle.trim()) errors.push(`${label}: role title is required.`);
    if (!exp.startDate) errors.push(`${label}: start date is required.`);
    if (exp.currentPosition && exp.endDate !== null) {
      errors.push(`${label}: marked as current, so it cannot also have an end date.`);
    }
    if (!exp.currentPosition && exp.endDate === null) {
      errors.push(`${label}: needs an end date, or mark it as your current position.`);
    }
    if (exp.startDate && exp.endDate && exp.endDate < exp.startDate) {
      errors.push(`${label}: end date cannot be before the start date.`);
    }
  });

  form.projects.forEach((proj, i) => {
    const label = `Project #${i + 1}`;
    if (!proj.name.trim()) errors.push(`${label}: name is required.`);
    if (proj.startDate && proj.endDate && proj.endDate < proj.startDate) {
      errors.push(`${label}: end date cannot be before the start date.`);
    }
  });

  form.certifications.forEach((cert, i) => {
    const label = `Certification #${i + 1}`;
    if (!cert.name.trim()) errors.push(`${label}: name is required.`);
    if (cert.issueDate && cert.expiryDate && cert.expiryDate < cert.issueDate) {
      errors.push(`${label}: expiry date cannot be before the issue date.`);
    }
  });

  const seenSkillNames = new Map<string, number>();
  form.skills.forEach((skill, i) => {
    const label = `Skill #${i + 1}`;
    if (!skill.name.trim()) {
      errors.push(`${label}: name is required.`);
      return;
    }
    const key = skill.name.trim().toLowerCase();
    if (seenSkillNames.has(key)) {
      errors.push(
        `${label} ("${skill.name.trim()}") duplicates skill #${seenSkillNames.get(key)! + 1} — skill names must be unique (case-insensitive).`,
      );
    } else {
      seenSkillNames.set(key, i);
    }
  });

  form.achievements.forEach((ach, i) => {
    const label = `Achievement #${i + 1}`;
    if (!ach.title.trim()) errors.push(`${label}: title is required.`);
  });

  return errors;
}
