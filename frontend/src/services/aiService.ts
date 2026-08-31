import api from "@/services/api";
import type { Page } from "@/types/academic";
import type {
  AIChatTurnResponse,
  AIConversationCreateRequest,
  AIConversationDetailResponse,
  AIConversationRenameRequest,
  AIConversationResponse,
  AIExplainRequest,
  AIFeature,
  AIMcqRequest,
  AIMessageCreateRequest,
  AIModelResponse,
  AIPracticeQuestionsRequest,
  AIStatusResponse,
  AIStudentContextResponse,
  AIStudyPlanGenerateRequest,
  AIStudyPlanItemRequest,
  AIStudyPlanResponse,
  AIStudyPlanStatus,
  AIStudyPlanSummaryResponse,
  AIStudyPlanType,
  AIStudyPlanUpdateRequest,
} from "@/types/ai";

/**
 * Typed wrappers around `/api/ai` and `/api/ai/study-plans`, per the Phase 6 contract.
 * Every AI conversation/plan endpoint is STUDENT-only end to end except `status`
 * (any authenticated role) and `models` (ADMIN only) — enforced server-side, not here.
 *
 *   GET    /api/ai/status                                  -> 200 AIStatusResponse
 *   GET    /api/ai/models                                  -> 200 AIModelResponse[]        (ADMIN)
 *   GET    /api/ai/context                                 -> 200 AIStudentContextResponse (STUDENT)
 *   GET    /api/ai/conversations?feature&q&page&size        -> 200 Page<AIConversationResponse>
 *   POST   /api/ai/conversations       AIConversationCreateRequest -> 201 AIConversationDetailResponse
 *   GET    /api/ai/conversations/{id}                       -> 200 AIConversationDetailResponse
 *   PUT    /api/ai/conversations/{id}  AIConversationRenameRequest -> 200 AIConversationResponse
 *   DELETE /api/ai/conversations/{id}                       -> 204
 *   POST   /api/ai/conversations/{id}/messages AIMessageCreateRequest -> 200 AIChatTurnResponse
 *   POST   /api/ai/explain             AIExplainRequest            -> 200 AIChatTurnResponse
 *   POST   /api/ai/practice-questions  AIPracticeQuestionsRequest  -> 200 AIChatTurnResponse
 *   POST   /api/ai/mcqs                AIMcqRequest                -> 200 AIChatTurnResponse
 *   POST   /api/ai/study-plans/generate           AIStudyPlanGenerateRequest -> 201 AIStudyPlanResponse
 *   POST   /api/ai/study-plans/revision-schedule  (same body)                -> 201 AIStudyPlanResponse
 *   GET    /api/ai/study-plans?planType&status&page&size    -> 200 Page<AIStudyPlanSummaryResponse>
 *   GET    /api/ai/study-plans/{id}                          -> 200 AIStudyPlanResponse
 *   PUT    /api/ai/study-plans/{id}    AIStudyPlanUpdateRequest -> 200 AIStudyPlanResponse
 *   DELETE /api/ai/study-plans/{id}                          -> 204
 *   POST   /api/ai/study-plans/{id}/items          AIStudyPlanItemRequest -> 201 AIStudyPlanResponse
 *   PUT    /api/ai/study-plans/{id}/items/{itemId} AIStudyPlanItemRequest -> 200 AIStudyPlanResponse
 *   DELETE /api/ai/study-plans/{id}/items/{itemId}                        -> 200 AIStudyPlanResponse
 *
 * Query parameter names are exact — `feature`, `q`, `planType`, `status`, `page`,
 * `size` — a mismatch silently returns unfiltered results (this shipped once already).
 */

const BASE = "/api/ai";
const STUDY_PLANS_BASE = "/api/ai/study-plans";

// ---------------------------------------------------------------------------------
// Status / models / academic context
// ---------------------------------------------------------------------------------

export async function getStatus(): Promise<AIStatusResponse> {
  const { data } = await api.get<AIStatusResponse>(`${BASE}/status`);
  return data;
}

/** ADMIN only. */
export async function listModels(): Promise<AIModelResponse[]> {
  const { data } = await api.get<AIModelResponse[]>(`${BASE}/models`);
  return data;
}

/** STUDENT only — the live academic record answers are grounded in. */
export async function getContext(): Promise<AIStudentContextResponse> {
  const { data } = await api.get<AIStudentContextResponse>(`${BASE}/context`);
  return data;
}

// ---------------------------------------------------------------------------------
// Conversations
// ---------------------------------------------------------------------------------

export interface ConversationListParams {
  feature?: AIFeature;
  q?: string;
  page?: number;
  size?: number;
}

export async function listConversations(
  params: ConversationListParams = {},
): Promise<Page<AIConversationResponse>> {
  const { data } = await api.get<Page<AIConversationResponse>>(`${BASE}/conversations`, { params });
  return data;
}

export async function createConversation(
  payload: AIConversationCreateRequest,
): Promise<AIConversationDetailResponse> {
  const { data } = await api.post<AIConversationDetailResponse>(`${BASE}/conversations`, payload);
  return data;
}

export async function getConversation(id: number): Promise<AIConversationDetailResponse> {
  const { data } = await api.get<AIConversationDetailResponse>(`${BASE}/conversations/${id}`);
  return data;
}

export async function renameConversation(
  id: number,
  payload: AIConversationRenameRequest,
): Promise<AIConversationResponse> {
  const { data } = await api.put<AIConversationResponse>(`${BASE}/conversations/${id}`, payload);
  return data;
}

export async function deleteConversation(id: number): Promise<void> {
  await api.delete(`${BASE}/conversations/${id}`);
}

export async function sendMessage(
  conversationId: number,
  payload: AIMessageCreateRequest,
): Promise<AIChatTurnResponse> {
  const { data } = await api.post<AIChatTurnResponse>(
    `${BASE}/conversations/${conversationId}/messages`,
    payload,
  );
  return data;
}

// ---------------------------------------------------------------------------------
// Grounded feature shortcuts — each opens its own conversation
// ---------------------------------------------------------------------------------

export async function explain(payload: AIExplainRequest): Promise<AIChatTurnResponse> {
  const { data } = await api.post<AIChatTurnResponse>(`${BASE}/explain`, payload);
  return data;
}

export async function practiceQuestions(payload: AIPracticeQuestionsRequest): Promise<AIChatTurnResponse> {
  const { data } = await api.post<AIChatTurnResponse>(`${BASE}/practice-questions`, payload);
  return data;
}

export async function mcqs(payload: AIMcqRequest): Promise<AIChatTurnResponse> {
  const { data } = await api.post<AIChatTurnResponse>(`${BASE}/mcqs`, payload);
  return data;
}

// ---------------------------------------------------------------------------------
// Study plans
// ---------------------------------------------------------------------------------

export async function generateStudyPlan(
  payload: AIStudyPlanGenerateRequest,
): Promise<AIStudyPlanResponse> {
  const { data } = await api.post<AIStudyPlanResponse>(`${STUDY_PLANS_BASE}/generate`, payload);
  return data;
}

export async function generateRevisionSchedule(
  payload: AIStudyPlanGenerateRequest,
): Promise<AIStudyPlanResponse> {
  const { data } = await api.post<AIStudyPlanResponse>(`${STUDY_PLANS_BASE}/revision-schedule`, payload);
  return data;
}

export interface StudyPlanListParams {
  planType?: AIStudyPlanType;
  status?: AIStudyPlanStatus;
  page?: number;
  size?: number;
}

export async function listStudyPlans(
  params: StudyPlanListParams = {},
): Promise<Page<AIStudyPlanSummaryResponse>> {
  const { data } = await api.get<Page<AIStudyPlanSummaryResponse>>(STUDY_PLANS_BASE, { params });
  return data;
}

export async function getStudyPlan(id: number): Promise<AIStudyPlanResponse> {
  const { data } = await api.get<AIStudyPlanResponse>(`${STUDY_PLANS_BASE}/${id}`);
  return data;
}

export async function updateStudyPlan(
  id: number,
  payload: AIStudyPlanUpdateRequest,
): Promise<AIStudyPlanResponse> {
  const { data } = await api.put<AIStudyPlanResponse>(`${STUDY_PLANS_BASE}/${id}`, payload);
  return data;
}

export async function deleteStudyPlan(id: number): Promise<void> {
  await api.delete(`${STUDY_PLANS_BASE}/${id}`);
}

export async function addStudyPlanItem(
  planId: number,
  payload: AIStudyPlanItemRequest,
): Promise<AIStudyPlanResponse> {
  const { data } = await api.post<AIStudyPlanResponse>(`${STUDY_PLANS_BASE}/${planId}/items`, payload);
  return data;
}

export async function updateStudyPlanItem(
  planId: number,
  itemId: number,
  payload: AIStudyPlanItemRequest,
): Promise<AIStudyPlanResponse> {
  const { data } = await api.put<AIStudyPlanResponse>(
    `${STUDY_PLANS_BASE}/${planId}/items/${itemId}`,
    payload,
  );
  return data;
}

export async function deleteStudyPlanItem(planId: number, itemId: number): Promise<AIStudyPlanResponse> {
  const { data } = await api.delete<AIStudyPlanResponse>(`${STUDY_PLANS_BASE}/${planId}/items/${itemId}`);
  return data;
}
