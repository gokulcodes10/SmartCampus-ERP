import api from "@/services/api";
import type {
  InterviewListParams,
  InterviewPage,
  InterviewRescheduleRequest,
  InterviewResponse,
  InterviewScheduleRequest,
  InterviewStatusUpdateRequest,
  InterviewUpdateRequest,
} from "@/types/interview";

/**
 * Typed wrappers around `/api/interviews`, per the Phase 10 implementation contract (§6):
 *
 *   GET    /api/interviews?status&studentId&from&to&q&page&size&sort -> 200 Page<InterviewResponse>
 *          STUDENT: forced to own rows server-side (a supplied studentId is ignored,
 *          never honoured). ADMIN: all rows, studentId filter honoured. FACULTY: 403.
 *   GET    /api/interviews/upcoming?limit                -> 200 InterviewResponse[] (NOT paged, STUDENT)
 *          status IN (SCHEDULED, RESCHEDULED) AND scheduledStart >= now, ascending.
 *          limit defaults to 5, clamped server-side to [1, 20].
 *   GET    /api/interviews/{id}                           -> 200 InterviewResponse. Owner student or ADMIN; else 404.
 *   POST   /api/interviews          InterviewScheduleRequest -> 201 InterviewResponse. ADMIN or STUDENT(self).
 *          409 CONFLICT on an overlapping slot for the same student — surface the
 *          server's message verbatim (it names the clashing window).
 *   PUT    /api/interviews/{id}     InterviewUpdateRequest   -> 200 InterviewResponse. ADMIN or owner STUDENT.
 *          Deliberately carries no times and no status — those have their own endpoints.
 *   PUT    /api/interviews/{id}/reschedule InterviewRescheduleRequest -> 200. ADMIN or owner STUDENT.
 *          409 CONFLICT on overlap, same as POST.
 *   PUT    /api/interviews/{id}/status     InterviewStatusUpdateRequest -> 200. ADMIN or owner STUDENT.
 *          400 BAD_REQUEST on an invalid status transition or a missing required field
 *          (e.g. cancellationReason when cancelling) — surface the server's message.
 *   DELETE /api/interviews/{id}                            -> 204. ADMIN only.
 *
 * *** QUERY-PARAMETER TRAP ***: `useServerTable` always sends the search box's value as
 * `search`; this backend reads `q`. That mapping happens here, not in the caller — see
 * `listInterviews` below. A mismatch silently returns an unfiltered page with no error
 * anywhere (this has shipped broken once already in this codebase).
 */

const BASE = "/api/interviews";

export async function listInterviews(params: InterviewListParams = {}): Promise<InterviewPage> {
  const { search, ...rest } = params;
  const { data } = await api.get<InterviewPage>(BASE, {
    params: { ...rest, q: search || undefined },
  });
  return data;
}

/** STUDENT only. Not paged. `limit` defaults to 5 and is clamped server-side to [1, 20]. */
export async function listUpcoming(limit = 5): Promise<InterviewResponse[]> {
  const { data } = await api.get<InterviewResponse[]>(`${BASE}/upcoming`, { params: { limit } });
  return data;
}

export async function getInterview(id: number): Promise<InterviewResponse> {
  const { data } = await api.get<InterviewResponse>(`${BASE}/${id}`);
  return data;
}

/** ADMIN or STUDENT scheduling for self. 409 on an overlapping slot for the same student. */
export async function scheduleInterview(payload: InterviewScheduleRequest): Promise<InterviewResponse> {
  const { data } = await api.post<InterviewResponse>(BASE, payload);
  return data;
}

export async function updateInterview(
  id: number,
  payload: InterviewUpdateRequest,
): Promise<InterviewResponse> {
  const { data } = await api.put<InterviewResponse>(`${BASE}/${id}`, payload);
  return data;
}

/** 409 on an overlapping slot for the same student (the interview being rescheduled excluded). */
export async function rescheduleInterview(
  id: number,
  payload: InterviewRescheduleRequest,
): Promise<InterviewResponse> {
  const { data } = await api.put<InterviewResponse>(`${BASE}/${id}/reschedule`, payload);
  return data;
}

export async function updateInterviewStatus(
  id: number,
  payload: InterviewStatusUpdateRequest,
): Promise<InterviewResponse> {
  const { data } = await api.put<InterviewResponse>(`${BASE}/${id}/status`, payload);
  return data;
}

/** ADMIN only. */
export async function deleteInterview(id: number): Promise<void> {
  await api.delete(`${BASE}/${id}`);
}
