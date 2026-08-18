# context/

React Context providers for cross-cutting app state — primarily
`AuthContext` (current user, role, JWT, login/logout), added in Phase 2.
Keep contexts narrow (auth, theme, notifications) rather than one large
global store; page/feature-local state stays in the page itself.
