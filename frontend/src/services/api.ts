import axios from "axios";

import { setupAuthInterceptors } from "@/services/authInterceptors";

/**
 * Shared axios instance for all backend calls.
 *
 * Base URL comes from VITE_API_BASE_URL, defaulting to the local backend
 * (see docker-compose.yml / backend application config — port 8080).
 *
 * Phase 2: setupAuthInterceptors attaches the JWT from local storage
 * (`Authorization: Bearer <token>`) to every request and, on a 401 from an
 * already-authenticated call, clears it and hands off to AuthContext's
 * onUnauthorized subscriber — see services/authInterceptors.ts.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
  headers: {
    "Content-Type": "application/json",
  },
});

setupAuthInterceptors(api);

export default api;
