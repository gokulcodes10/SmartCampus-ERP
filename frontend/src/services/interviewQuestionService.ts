import api from "@/services/api";
import type {
  InterviewGeneratedQuestionsResponse,
  InterviewQuestionCreateRequest,
  InterviewQuestionGenerateRequest,
  InterviewQuestionListParams,
  InterviewQuestionPage,
  InterviewQuestionProgressRequest,
  InterviewQuestionResponse,
  InterviewProgressSummaryResponse,
  InterviewQuestionUpdateRequest,
} from "@/types/interview";

/**
 * Typed wrappers around `/api/interview-questions`, per the Phase 10 implementation
 * contract (§6):
 *
 *   GET    /api/interview-questions?category&difficulty&companyName&source&q&bookmarked
 *          &completed&mine&page&size&sort               -> 200 Page<InterviewQuestionResponse>
 *          any authenticated role. VISIBILITY is enforced server-side: STUDENT sees the
 *          global bank plus their own AI rows; FACULTY/ADMIN see the global bank only.
 *          `bookmarked`/`completed`/`mine` are honoured for STUDENT only (ignored for
 *          other roles server-side).
 *   GET    /api/interview-questions/progress/summary     -> 200 InterviewProgressSummaryResponse (STUDENT)
 *   GET    /api/interview-questions/{id}                 -> 200 InterviewQuestionResponse
 *          Not visible to the caller -> 404, never 403.
 *   POST   /api/interview-questions      InterviewQuestionCreateRequest -> 201 InterviewQuestionResponse (ADMIN)
 *   PUT    /api/interview-questions/{id} InterviewQuestionUpdateRequest -> 200 InterviewQuestionResponse (ADMIN)
 *   DELETE /api/interview-questions/{id}                  -> 204
 *          ADMIN: only a global-bank row (404 for a student's private row).
 *          STUDENT: only their own row. FACULTY: 403.
 *   PUT    /api/interview-questions/{id}/progress InterviewQuestionProgressRequest
 *          -> 200 InterviewQuestionResponse (STUDENT) — upsert on (student, question).
 *          Both request fields are nullable: omitted/null means "leave unchanged".
 *   POST   /api/interview-questions/generate InterviewQuestionGenerateRequest
 *          -> 201 InterviewGeneratedQuestionsResponse (STUDENT)
 *          429 RATE_LIMIT_EXCEEDED when the AI rate limiter denies; 503 AI_UNAVAILABLE
 *          when the provider is unconfigured or fails — surface the server's message
 *          verbatim (see extractErrorMessage), never a fake generated question.
 *
 * *** QUERY-PARAMETER TRAP ***: `useServerTable` always sends the search box's value as
 * `search`; this backend reads `q`. That mapping happens here, not in the caller — see
 * `listInterviewQuestions` below. A mismatch silently returns an unfiltered page with no
 * error anywhere (this has shipped broken once already in this codebase).
 */

const BASE = "/api/interview-questions";

export async function listInterviewQuestions(
  params: InterviewQuestionListParams = {},
): Promise<InterviewQuestionPage> {
  const { search, ...rest } = params;
  const { data } = await api.get<InterviewQuestionPage>(BASE, {
    params: { ...rest, q: search || undefined },
  });
  return data;
}

export async function getProgressSummary(): Promise<InterviewProgressSummaryResponse> {
  const { data } = await api.get<InterviewProgressSummaryResponse>(`${BASE}/progress/summary`);
  return data;
}

export async function getInterviewQuestion(id: number): Promise<InterviewQuestionResponse> {
  const { data } = await api.get<InterviewQuestionResponse>(`${BASE}/${id}`);
  return data;
}

/** ADMIN only. Always persisted as source=CURATED, ownerStudent=null. */
export async function createInterviewQuestion(
  payload: InterviewQuestionCreateRequest,
): Promise<InterviewQuestionResponse> {
  const { data } = await api.post<InterviewQuestionResponse>(BASE, payload);
  return data;
}

/** ADMIN only, and only on a global-bank row. */
export async function updateInterviewQuestion(
  id: number,
  payload: InterviewQuestionUpdateRequest,
): Promise<InterviewQuestionResponse> {
  const { data } = await api.put<InterviewQuestionResponse>(`${BASE}/${id}`, payload);
  return data;
}

export async function deleteInterviewQuestion(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}

/** STUDENT only. Returns the FULL updated question (flags included) — re-render from this, not optimistically. */
export async function updateProgress(
  id: number,
  payload: InterviewQuestionProgressRequest,
): Promise<InterviewQuestionResponse> {
  const { data } = await api.put<InterviewQuestionResponse>(`${BASE}/${id}/progress`, payload);
  return data;
}

/** STUDENT only. Persists each generated question as source=AI_GENERATED, owned by the caller. */
export async function generateQuestions(
  payload: InterviewQuestionGenerateRequest,
): Promise<InterviewGeneratedQuestionsResponse> {
  const { data } = await api.post<InterviewGeneratedQuestionsResponse>(`${BASE}/generate`, payload);
  return data;
}
