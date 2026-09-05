import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeftIcon, LockIcon } from "lucide-react";

import { DuplicateResumeDialog } from "@/components/resume/DuplicateResumeDialog";
import { RepeatableSection } from "@/components/resume/RepeatableSection";
import { ResumeAchievementFields } from "@/components/resume/ResumeAchievementFields";
import { ResumeCertificationFields } from "@/components/resume/ResumeCertificationFields";
import { ResumeEducationFields } from "@/components/resume/ResumeEducationFields";
import { ResumeExperienceFields } from "@/components/resume/ResumeExperienceFields";
import { ResumePdfPreview } from "@/components/resume/ResumePdfPreview";
import { ResumeProjectFields } from "@/components/resume/ResumeProjectFields";
import { ResumeSkillFields } from "@/components/resume/ResumeSkillFields";
import { SectionCard } from "@/components/resume/SectionCard";
import { TemplatePicker } from "@/components/resume/TemplatePicker";
import { validateResume } from "@/components/resume/resumeValidation";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import * as resumeService from "@/services/resumeService";
import { extractErrorMessage } from "@/utils/apiError";
import type {
  ResumeAchievementRequest,
  ResumeCertificationRequest,
  ResumeEducationRequest,
  ResumeExperienceRequest,
  ResumePrefillResponse,
  ResumeProjectRequest,
  ResumeResponse,
  ResumeSaveRequest,
  ResumeSkillRequest,
} from "@/types/resume";

function emptySaveRequest(): ResumeSaveRequest {
  return {
    title: "",
    template: "CLASSIC",
    fullName: "",
    email: "",
    phone: null,
    location: null,
    linkedinUrl: null,
    githubUrl: null,
    portfolioUrl: null,
    summary: null,
    educations: [],
    experiences: [],
    projects: [],
    certifications: [],
    skills: [],
    achievements: [],
  };
}

function prefillToSaveRequest(prefill: ResumePrefillResponse): ResumeSaveRequest {
  return {
    ...emptySaveRequest(),
    title: prefill.suggestedTitle,
    fullName: prefill.fullName,
    email: prefill.email,
    phone: prefill.phone,
    location: prefill.location,
    educations: prefill.educations,
  };
}

function stripIdAndOrder<T extends { id: number; displayOrder: number }>(item: T): Omit<T, "id" | "displayOrder"> {
  const { id: _id, displayOrder: _displayOrder, ...rest } = item;
  return rest;
}

function responseToSaveRequest(resume: ResumeResponse): ResumeSaveRequest {
  return {
    title: resume.title,
    template: resume.template,
    fullName: resume.fullName,
    email: resume.email,
    phone: resume.phone,
    location: resume.location,
    linkedinUrl: resume.linkedinUrl,
    githubUrl: resume.githubUrl,
    portfolioUrl: resume.portfolioUrl,
    summary: resume.summary,
    educations: resume.educations.map(stripIdAndOrder),
    experiences: resume.experiences.map(stripIdAndOrder),
    projects: resume.projects.map(stripIdAndOrder),
    certifications: resume.certifications.map(stripIdAndOrder),
    skills: resume.skills.map(stripIdAndOrder),
    achievements: resume.achievements.map(stripIdAndOrder),
  };
}

function newEducation(): ResumeEducationRequest {
  return {
    institution: "",
    degree: null,
    fieldOfStudy: null,
    startYear: null,
    endYear: null,
    gradeValue: null,
    gradeScale: null,
  };
}

function newExperience(): ResumeExperienceRequest {
  return {
    companyName: "",
    roleTitle: "",
    location: null,
    employmentType: null,
    startDate: "",
    endDate: null,
    currentPosition: false,
    description: null,
  };
}

function newProject(): ResumeProjectRequest {
  return {
    name: "",
    description: null,
    techStack: null,
    projectUrl: null,
    repositoryUrl: null,
    startDate: null,
    endDate: null,
  };
}

function newCertification(): ResumeCertificationRequest {
  return {
    name: "",
    issuer: null,
    issueDate: null,
    expiryDate: null,
    credentialId: null,
    credentialUrl: null,
  };
}

function newSkill(): ResumeSkillRequest {
  return { name: "", category: "TECHNICAL", proficiency: null };
}

function newAchievement(): ResumeAchievementRequest {
  return { title: "", description: null, issuer: null, achievedOn: null };
}

/**
 * `/student/resumes/new` (create) and `/student/resumes/:id` (edit). One form covers
 * the header block, template picker and all six repeatable sections; the preview pane
 * renders the real generated PDF (never an HTML lookalike — see ResumePdfPreview).
 */
export default function StudentResumeEditorPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isEditMode = id !== undefined;
  const resumeId = isEditMode ? Number(id) : null;

  const [form, setForm] = useState<ResumeSaveRequest | null>(null);
  const [savedResume, setSavedResume] = useState<ResumeResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [previewReloadToken, setPreviewReloadToken] = useState(0);

  const [duplicateOpen, setDuplicateOpen] = useState(false);
  const [isDuplicating, setIsDuplicating] = useState(false);
  const [duplicateError, setDuplicateError] = useState<string | null>(null);

  // Reset loading/error during render when the target record changes, rather than as
  // the first statements inside the effect — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes.
  const loadKey = `${isEditMode}:${resumeId}`;
  const [fetchedKey, setFetchedKey] = useState<string | null>(null);
  if (loadKey !== fetchedKey) {
    setFetchedKey(loadKey);
    setIsLoading(true);
    setLoadError(null);
  }

  useEffect(() => {
    let cancelled = false;

    if (isEditMode && resumeId !== null) {
      resumeService
        .getResume(resumeId)
        .then((resume) => {
          if (cancelled) return;
          setSavedResume(resume);
          setForm(responseToSaveRequest(resume));
        })
        .catch((err) => {
          if (!cancelled) setLoadError(extractErrorMessage(err, "Failed to load this resume."));
        })
        .finally(() => {
          if (!cancelled) setIsLoading(false);
        });
    } else {
      resumeService
        .getResumePrefill()
        .then((prefill) => {
          if (cancelled) return;
          setForm(prefillToSaveRequest(prefill));
        })
        .catch((err) => {
          if (!cancelled) setLoadError(extractErrorMessage(err, "Failed to load your profile details."));
        })
        .finally(() => {
          if (!cancelled) setIsLoading(false);
        });
    }

    return () => {
      cancelled = true;
    };
  }, [isEditMode, resumeId]);

  const locked = savedResume?.locked ?? false;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!form || locked) return;

    const errors = validateResume(form);
    setValidationErrors(errors);
    if (errors.length > 0) return;

    setSaveError(null);
    setIsSaving(true);
    try {
      const result =
        isEditMode && resumeId !== null
          ? await resumeService.updateResume(resumeId, form)
          : await resumeService.createResume(form);
      setSavedResume(result);
      setForm(responseToSaveRequest(result));
      setPreviewReloadToken((t) => t + 1);
      if (!isEditMode) {
        navigate(`/student/resumes/${result.id}`, { replace: true });
      }
    } catch (err) {
      setSaveError(extractErrorMessage(err, "Failed to save this resume."));
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDuplicate(title: string) {
    if (!savedResume) return;
    setIsDuplicating(true);
    setDuplicateError(null);
    try {
      const copy = await resumeService.duplicateResume(savedResume.id, { title });
      setDuplicateOpen(false);
      navigate(`/student/resumes/${copy.id}`);
    } catch (err) {
      setDuplicateError(extractErrorMessage(err, "Failed to duplicate this resume."));
    } finally {
      setIsDuplicating(false);
    }
  }

  if (isLoading) {
    return <p className="py-8 text-center text-muted-foreground">Loading…</p>;
  }

  if (loadError || !form) {
    return (
      <Alert variant="destructive">
        <AlertDescription>{loadError ?? "This resume could not be loaded."}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-4">
      <Link
        to="/student/resumes"
        className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeftIcon className="size-3.5" />
        Back to my resumes
      </Link>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">
          {isEditMode ? form.title || "Edit resume" : "New resume"}
        </h1>
        {savedResume && (
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              setDuplicateError(null);
              setDuplicateOpen(true);
            }}
          >
            Duplicate
          </Button>
        )}
      </div>

      {locked && (
        <Alert>
          <LockIcon />
          <AlertTitle>This version is locked</AlertTitle>
          <AlertDescription>
            It&rsquo;s attached to a placement application, so it&rsquo;s permanently read-only. Duplicate it to
            keep making changes.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <form onSubmit={handleSubmit} className="space-y-4">
          {validationErrors.length > 0 && (
            <Alert variant="destructive">
              <AlertTitle>Fix the following before saving</AlertTitle>
              <AlertDescription>
                <ul className="list-inside list-disc space-y-0.5">
                  {validationErrors.map((msg, i) => (
                    <li key={i}>{msg}</li>
                  ))}
                </ul>
              </AlertDescription>
            </Alert>
          )}
          {saveError && (
            <Alert variant="destructive">
              <AlertDescription>{saveError}</AlertDescription>
            </Alert>
          )}

          <SectionCard title="Header" description="Shown at the top of every template.">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5 sm:col-span-2">
                <Label>Resume title *</Label>
                <Input
                  value={form.title}
                  maxLength={150}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  placeholder="e.g. Backend Engineer Resume"
                />
              </div>
              <div className="space-y-1.5">
                <Label>Full name *</Label>
                <Input
                  value={form.fullName}
                  maxLength={150}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, fullName: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>Email *</Label>
                <Input
                  type="email"
                  value={form.email}
                  maxLength={255}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>Phone</Label>
                <Input
                  value={form.phone ?? ""}
                  maxLength={20}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, phone: e.target.value || null })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>Location</Label>
                <Input
                  value={form.location ?? ""}
                  maxLength={150}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, location: e.target.value || null })}
                />
              </div>
              <div className="space-y-1.5">
                <Label>LinkedIn URL</Label>
                <Input
                  value={form.linkedinUrl ?? ""}
                  maxLength={255}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, linkedinUrl: e.target.value || null })}
                  placeholder="https://linkedin.com/in/…"
                />
              </div>
              <div className="space-y-1.5">
                <Label>GitHub URL</Label>
                <Input
                  value={form.githubUrl ?? ""}
                  maxLength={255}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, githubUrl: e.target.value || null })}
                  placeholder="https://github.com/…"
                />
              </div>
              <div className="space-y-1.5">
                <Label>Portfolio URL</Label>
                <Input
                  value={form.portfolioUrl ?? ""}
                  maxLength={255}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, portfolioUrl: e.target.value || null })}
                  placeholder="https://…"
                />
              </div>
              <div className="space-y-1.5 sm:col-span-2">
                <Label>Summary</Label>
                <textarea
                  className="min-h-24 w-full rounded-lg border border-border bg-background px-3 py-2 text-sm outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                  value={form.summary ?? ""}
                  maxLength={20000}
                  disabled={locked}
                  onChange={(e) => setForm({ ...form, summary: e.target.value || null })}
                  placeholder="A short professional summary…"
                />
              </div>
            </div>
          </SectionCard>

          <SectionCard title="Template">
            <TemplatePicker
              value={form.template}
              onChange={(template) => setForm({ ...form, template })}
              disabled={locked}
            />
          </SectionCard>

          <RepeatableSection
            title="Education"
            items={form.educations}
            onChange={(educations) => setForm({ ...form, educations })}
            newItem={newEducation}
            itemLabel="Education"
            addLabel="Add education"
            emptyText="No education added yet."
            disabled={locked}
            renderItem={(item, _i, update) => (
              <ResumeEducationFields value={item} onChange={update} disabled={locked} />
            )}
          />

          <RepeatableSection
            title="Experience"
            items={form.experiences}
            onChange={(experiences) => setForm({ ...form, experiences })}
            newItem={newExperience}
            itemLabel="Experience"
            addLabel="Add experience"
            emptyText="No experience added yet."
            disabled={locked}
            renderItem={(item, _i, update) => (
              <ResumeExperienceFields value={item} onChange={update} disabled={locked} />
            )}
          />

          <RepeatableSection
            title="Projects"
            items={form.projects}
            onChange={(projects) => setForm({ ...form, projects })}
            newItem={newProject}
            itemLabel="Project"
            addLabel="Add project"
            emptyText="No projects added yet."
            disabled={locked}
            renderItem={(item, _i, update) => (
              <ResumeProjectFields value={item} onChange={update} disabled={locked} />
            )}
          />

          <RepeatableSection
            title="Certifications"
            items={form.certifications}
            onChange={(certifications) => setForm({ ...form, certifications })}
            newItem={newCertification}
            itemLabel="Certification"
            addLabel="Add certification"
            emptyText="No certifications added yet."
            disabled={locked}
            renderItem={(item, _i, update) => (
              <ResumeCertificationFields value={item} onChange={update} disabled={locked} />
            )}
          />

          <RepeatableSection
            title="Skills"
            items={form.skills}
            onChange={(skills) => setForm({ ...form, skills })}
            newItem={newSkill}
            itemLabel="Skill"
            addLabel="Add skill"
            emptyText="No skills added yet."
            disabled={locked}
            renderItem={(item, _i, update) => (
              <ResumeSkillFields value={item} onChange={update} disabled={locked} />
            )}
          />

          <RepeatableSection
            title="Achievements"
            items={form.achievements}
            onChange={(achievements) => setForm({ ...form, achievements })}
            newItem={newAchievement}
            itemLabel="Achievement"
            addLabel="Add achievement"
            emptyText="No achievements added yet."
            disabled={locked}
            renderItem={(item, _i, update) => (
              <ResumeAchievementFields value={item} onChange={update} disabled={locked} />
            )}
          />

          {!locked && (
            <div className="flex justify-end">
              <Button type="submit" disabled={isSaving}>
                {isSaving ? "Saving…" : isEditMode ? "Save changes" : "Create resume"}
              </Button>
            </div>
          )}
        </form>

        <div className="lg:sticky lg:top-4 lg:self-start">
          <ResumePdfPreview resumeId={savedResume?.id ?? null} reloadToken={previewReloadToken} />
        </div>
      </div>

      {savedResume && (
        <DuplicateResumeDialog
          open={duplicateOpen}
          onOpenChange={setDuplicateOpen}
          sourceTitle={savedResume.title}
          onConfirm={handleDuplicate}
          isSubmitting={isDuplicating}
          error={duplicateError}
        />
      )}
    </div>
  );
}
