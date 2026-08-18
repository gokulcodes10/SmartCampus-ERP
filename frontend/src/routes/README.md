# routes/

`react-router-dom` route definitions: the top-level `<Routes>` tree, plus
`ProtectedRoute` / `RoleRoute` guards added in Phase 2 that check
`context/AuthContext` and redirect unauthenticated or unauthorized users to
login (per the Phase 2 checkpoint: 401 → redirect to login, role-based
routing per dashboard). Phase 1 ships no protected routes yet — only the
placeholder root route.
