# utils/

Small, pure, stateless helper functions with no React and no I/O — date
formatting, GPA/attendance display formatting, validation helpers, constants.
`components/ui/*` from shadcn keeps its own `lib/utils.ts` (the `cn()`
classname helper) separate from this folder; this folder is for
application/domain helpers, not UI-primitive plumbing.
