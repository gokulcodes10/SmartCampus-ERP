import { Badge } from "@/components/ui/badge";
import { OUTCOME_BADGE_VARIANT, OUTCOME_LABELS, STATUS_BADGE_VARIANT, STATUS_LABELS } from "@/components/interview/interviewLabels";
import type { InterviewOutcome, InterviewStatus } from "@/types/interview";

export function InterviewStatusBadge({ status }: { status: InterviewStatus }) {
  return <Badge variant={STATUS_BADGE_VARIANT[status]}>{STATUS_LABELS[status]}</Badge>;
}

export function InterviewOutcomeBadge({ outcome }: { outcome: InterviewOutcome }) {
  return <Badge variant={OUTCOME_BADGE_VARIANT[outcome]}>{OUTCOME_LABELS[outcome]}</Badge>;
}
