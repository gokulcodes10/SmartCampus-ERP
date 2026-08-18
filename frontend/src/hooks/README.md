# hooks/

Custom React hooks that encapsulate reusable stateful logic — e.g.
`useAuth()` (reads `context/AuthContext`), `usePagination()` for the §44
server-side pagination envelope, `useWebSocket()` for the Phase 11
notification stream. Hooks may call `services/` but must not contain raw
`fetch`/`axios` calls inline — route those through a service module.
