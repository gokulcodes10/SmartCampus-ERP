# services/

All backend I/O lives here — one module per API group (e.g. `authService.ts`,
`attendanceService.ts`, `aiService.ts`), each a thin wrapper around the shared
axios instance in `api.ts` that calls a real backend endpoint and returns
typed data. No mock data, no hard-coded responses (scope §69) — a service
that has no backend endpoint yet simply does not exist yet.

`api.ts` is the one axios instance every service imports; it is configured
here in Phase 1. The JWT request interceptor is added in Phase 2 once
auth exists.
