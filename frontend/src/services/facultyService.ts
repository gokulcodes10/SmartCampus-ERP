import api from "@/services/api";
import type {
  FacultyCreateRequest,
  FacultyListParams,
  FacultyResponse,
  FacultyUpdateRequest,
  Page,
} from "@/types/academic";

/**
 * Typed wrappers around `/api/faculty`, reconciled against the real
 * `smartcampus.controller.FacultyController`:
 *
 *   GET    /api/faculty?page&size&q&status&departmentId -> 200 Page<FacultyResponse> (ADMIN)
 *          (no `sort` param — results are always newest-first, `id DESC`)
 *   GET    /api/faculty/me                              -> 200 FacultyResponse       (FACULTY)
 *          The caller's own profile. 404 if the calling account has no faculty row.
 *   PUT    /api/faculty/{id}   FacultyUpdateRequest      -> 200 FacultyResponse       (ADMIN)
 *          `status` IS part of this payload (unlike Student's PUT) — this is how
 *          deactivate/reactivate are implemented for faculty, by sending the existing
 *          record back with `status` flipped.
 *   DELETE — none; deactivate via `status` (`faculty_subject_assignments.faculty_id` is
 *            RESTRICT, and there's no PENDING state to "undo" the way students have).
 *
 * Faculty creation is two calls, not one, and deliberately reuses infrastructure Phase 2
 * already built and tested rather than asking the Phase 3 backend to duplicate it:
 *
 *   1. POST /api/users   { email, password, fullName, role: "FACULTY" }  -> 201 UserResponse
 *      (`UserAdminController`, already implemented, ADMIN-only — see
 *      backend/src/main/java/smartcampus/controller/UserAdminController.java.)
 *   2. POST /api/faculty { userId, employeeCode, departmentId, designation }
 *      -> 201 FacultyResponse
 *      (Phase 3 backend — creates the `faculty` row for the just-provisioned user, with
 *      status defaulting to ACTIVE per the DB default.)
 *
 * `createFaculty` below runs both steps. If step 2 fails (e.g. duplicate employee code),
 * step 1 has already committed — the account exists but has no faculty profile yet. The
 * thrown error carries `accountCreated: true` in that case so the caller can tell the
 * admin the login was created and only the profile needs retrying, rather than silently
 * losing track of the orphaned account. A fully atomic single-endpoint alternative
 * (`POST /api/faculty` taking the account fields directly, mirroring how registration
 * creates User+Student together) is the better long-term contract — noted in this
 * agent's final report for the integrator/backend agent to consider.
 */

const BASE = "/api/faculty";

/**
 * The calling FACULTY user's own profile — `GET /api/faculty/me`.
 *
 * Used where a screen has to name the department the caller acts on behalf of (the
 * faculty announcements page, whose audience is always the caller's own department).
 * Throws 404 if the authenticated account has no faculty row, which is a real state:
 * an account can be provisioned with the FACULTY role before its profile is created.
 */
export async function getMyFacultyProfile(): Promise<FacultyResponse> {
  const { data } = await api.get<FacultyResponse>(`${BASE}/me`);
  return data;
}

export async function listFaculty(params: FacultyListParams = {}): Promise<Page<FacultyResponse>> {
  // useServerTable always sends the search box's value as `search` (shared across
  // every admin resource); FacultyController's query param is named `q`, and it has no
  // `sort` param at all, so both are translated/dropped here rather than in the shared
  // hook.
  const { search, sort: _sort, ...rest } = params;
  const { data } = await api.get<Page<FacultyResponse>>(BASE, {
    params: { ...rest, q: search },
  });
  return data;
}

export class FacultyCreationError extends Error {
  accountCreated: boolean;
  cause: unknown;

  constructor(message: string, accountCreated: boolean, cause: unknown) {
    super(message);
    this.name = "FacultyCreationError";
    this.accountCreated = accountCreated;
    this.cause = cause;
  }
}

export async function createFaculty(payload: FacultyCreateRequest): Promise<FacultyResponse> {
  let userId: number;
  try {
    const { data: user } = await api.post<{ id: number }>("/api/users", {
      email: payload.email,
      password: payload.password,
      fullName: payload.fullName,
      role: "FACULTY",
    });
    userId = user.id;
  } catch (err) {
    throw new FacultyCreationError("Failed to create the faculty login account.", false, err);
  }

  try {
    const { data } = await api.post<FacultyResponse>(BASE, {
      userId,
      employeeCode: payload.employeeCode,
      departmentId: payload.departmentId,
      designation: payload.designation,
    });
    return data;
  } catch (err) {
    throw new FacultyCreationError(
      "The login account was created, but the faculty profile could not be saved. " +
        "Contact an administrator to finish setting up this account.",
      true,
      err,
    );
  }
}

export async function updateFaculty(
  id: number,
  payload: FacultyUpdateRequest,
): Promise<FacultyResponse> {
  const { data } = await api.put<FacultyResponse>(`${BASE}/${id}`, payload);
  return data;
}
