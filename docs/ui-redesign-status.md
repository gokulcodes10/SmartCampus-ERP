# UI redesign — current status

Updated 2026-09-15. Records what the design work has produced so far and what
remains, so the next session can pick it up without re-reading the transcript.

---

## 1. Which design the app now follows

The application frontend has been redesigned to the visual language of
**`Posting Recommendation UI Mockups.html`** (repo root), a Claude Design canvas
export supplied for that purpose. Its content is gzip+base64 bundled inside the
file; unpack the manifest on the two long lines to read the artboards.

That design — not [`ui-design-plan.md`](./ui-design-plan.md) — is now the
authority on the application's appearance. The older plan proposed a different
direction (a serif display face, its own token set) and was **not** adopted. It
is kept for its §7 defect diagnosis, which was acted on; treat the rest as
superseded.

The design in one line, as its own cover note puts it: navy sidebar, soft
grey-blue canvas, white 14px-radius cards, indigo primary, pastel status pills,
dark-blue table headers. Typeface is Plus Jakarta Sans.

---

## 2. What shipped

Presentation only. No API, DTO or backend behaviour changed.

| Layer | Work |
|---|---|
| Tokens (`src/index.css`) | The whole palette replaced: navy `#1b2a4a` sidebar, `#eef1f7` canvas, indigo `#4f5bd5` primary, `#4a6591` table heads, the six pastel status pairs, a six-stop categorical chart ramp, `--radius` 14px, four KPI gradients, card/raised shadows. Light and dark both defined. Plus Jakarta Sans replaces Geist. |
| Primitives | `card` (14px, soft lift instead of a hairline ring, 17px/700 titles), `button` (indigo, 36px default, 10px radius), `badge` (6px pill + `success`/`warning`/`info`/`violet` variants), `table` (navy header band with rounded ends, `--rule` hairlines, tabular figures), `input`, `select`, `dialog`, `alert-dialog`, `alert` (pastel danger notice). |
| Shell | `DashboardLayout` — navy rail with wordmark, user chip, iconised nav, help block; white top bar with search affordance, bell, identity, avatar. `SidebarNav` gained icons and the indigo active chip. `MobileNavDrawer` matches. `AuthLayout` puts the login card on the navy ground. |
| New components | `analytics/KpiCard` (the design's gradient metric tile, null-honest per §60/§69), `layout/PageHeader` (24px/700 title + muted line), `charts/chartColors` (the single source of chart colour). |
| Dashboards | All three lead with a `PageHeader` and a four- or three-up `KpiCard` row. Figures the row now carries were removed from the `StatTile` grids below rather than shown twice; on the student dashboard the two cards that repeated attendance and CGPA were deleted and the tiles themselves became the links. |
| Charts | All 31 hardcoded hex values gone. `CategoryBarChart`, `DistributionDoughnutChart` and `TrendLineChart` resolve series, axis, grid and legend colour from tokens at render time, so charts follow the theme. `colors`/`color` props are now optional. |

### Defects fixed in the same pass

- **Every dropdown rendered its raw value instead of the option label** — 78
  dropdowns across 38 files showed `__ALL__` or a bare id. Base UI's
  `Select.Value` needs an `items` map on `Select.Root`; the shared wrapper in
  `components/ui/select.tsx` now derives that map by walking the `SelectItem`
  children already in the tree, so no call site changed. An explicit `items`
  prop still wins.
- Avatar initials skip honorifics — "Dr. Ramesh Iyer" is RI, not DI.
- The last row of every table no longer draws a trailing rule.

### Deliberate departures from the sampled design

The mockup's pastel pills and its emerald and blue KPI gradients carry small
type at roughly 3:1. Text tokens and those two gradients are darkened until the
small type on them clears WCAG AA (4.5:1); hue and saturation are unchanged, so
the palette still reads as the design's. Recorded in a comment at the top of the
token block.

---

## 3. Verification performed

- `npm run build`, `npm test` (81 passing, 16 files) and `npx oxlint` clean. The
  four remaining oxlint warnings are pre-existing `only-export-components`
  notices, unrelated to this work.
- 42 routes walked in real Chrome across all three roles at 1440px, plus the
  dashboards and analytics at 768px and 390px. **Horizontal overflow is zero on
  every route.** Login page and mobile nav drawer checked directly.
- Contrast computed for every token pair in use. All text pairs pass AA.
- No new console errors. The one warning seen is the pre-existing Base UI
  `nativeButton` notice (see below).

---

## 4. What remains

| Item | Note |
|---|---|
| **Dark mode is dormant** | A full dark palette is defined under `.dark`, but nothing ever sets that class — there is no theme provider or toggle. The tokens are ready; the toggle is not built, so dark mode is unverified in a browser. |
| **Performance-band colours clash with the design** | The four rows at `/admin/performance-bands` hold `#16A34A`, `#2563EB`, `#CA8A04`, `#DC2626`, and the classification doughnuts render straight from them. Against the new palette they read as harsh. These are admin-editable **data**, not presentation, so they were left alone; updating them is a one-screen edit whenever it is wanted. |
| **Base UI `nativeButton` warning** | Three pages log it. A non-native element is used where a native button is expected, which strips native button semantics. Pre-existing; untouched. |
| **Leftover test accounts** | The student list still shows "Integrator Verify" and similar from earlier verification sessions. An evaluator sees them immediately. |
| Search affordance | The top bar's search is presented as a label, not a working control, because nothing is wired behind it. Either implement it or keep it visibly inert. |
