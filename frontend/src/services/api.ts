import axios from "axios";

/**
 * Shared axios instance for all backend calls.
 *
 * Base URL comes from VITE_API_BASE_URL, defaulting to the local backend
 * (see docker-compose.yml / backend application config — port 8080).
 *
 * TODO(Phase 2): add a request interceptor that attaches the JWT from
 * AuthContext (`Authorization: Bearer <token>`) and a response interceptor
 * that redirects to /login on 401. No auth exists yet in Phase 1, so no
 * interceptor is added here — do not stub fake auth ahead of Phase 2.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
  headers: {
    "Content-Type": "application/json",
  },
});

export default api;
