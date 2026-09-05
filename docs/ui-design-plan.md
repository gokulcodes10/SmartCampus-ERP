# UI Design Plan — "Ledger"

A plan, not a change. Nothing in this document has been implemented. It records where the interface stands today, the direction chosen for it, the exact tokens to build against, and the order to do the work in.

Direction: **Ledger — the record is the hero.**
Scope: **full re-skin, including making dark mode reachable.**

---

## 1. Where the interface stands today

Findings from reading `frontend/src/index.css` and driving all 42 routes in a real browser. These are the reasons for the plan, so they are recorded rather than summarised.

**The theme is stock shadcn, untouched.** Every colour token is `oklch(x 0 0)` — chroma zero, pure greyscale. The only exceptions are the destructive red and a leftover blue-violet in the dark sidebar. Nothing in the palette identifies this product.

**The five chart tokens are five greys, and nothing uses them.** For an analytics product this is a functional problem, not a cosmetic one. Charts reach for hardcoded hex instead, and three unrelated palettes compete:

| Source | What it uses |
|---|---|
| Student analytics | Tailwind 600s — blue, green, amber |
| Admin and placement analytics | A separate eight-colour set |
| Performance bands | Four more, configured in the database |

That is 31 hardcoded hex values across pages and components, plus one stray purple in the student GPA chart.

**Only the database treats colour as meaning.** `performance_bands.color_hex` is admin-editable and drives `ClassificationBadge`. Nothing else in the interface follows that idea, so attendance warnings, application statuses and submission verdicts are all rendered in greyscale badge variants.

**Dark mode cannot turn on.** The `.dark` token block is complete and 18 files carry `dark:` variants, but nothing anywhere applies the `dark` class. It is dead code today.

**Structurally it is the SaaS-card kit.** Identical rounded cards, one border radius on everything regardless of hierarchy, and four stat tiles of equal weight — so on the student analytics page CGPA reads no louder than the trend window filter beside it. The three roles are visually indistinguishable, which is a missed opportunity in a product whose entire authorization model is about role.

What is already good and should survive: Geist is a defensible UI face, spacing is consistent, empty states are well written, and semantic colour already lives as configuration rather than as constants.

---

## 2. The direction

The subject is an academic record for an engineering college, read by three audiences. The product's own claim is that every number traces to a real database aggregation and nothing is fabricated. The design should make numbers look **measured** — the way a mark sheet or an attendance register does.

So the hero is the record itself: figures in aligned columns, structure carried by ruled lines rather than by boxes, and colour reserved entirely for meaning.

Three things this deliberately is **not**, because they are the defaults rather than choices: the cream-ground, high-contrast-serif, terracotta-accent look; the near-black ground with one acid accent; and the current card kit with a uniform radius and a soft grey shadow under everything.

---

## 3. Colour tokens

### Light

| Token | Hex | Role |
|---|---|---|
| `--background` | `#FBFAF7` | Page ground. Bond paper, cool, deliberately not cream. |
| `--card` | `#FFFFFF` | Raised surface. |
| `--foreground` | `#16202E` | Body text. A true navy-black that reads as ink. |
| `--muted-foreground` | `#5A6472` | Secondary text, labels, meta. |
| `--border` | `#E2E1DC` | Hairline rules and dividers. |
| `--input` | `#D8D6D0` | Field edges, one step darker than a rule so controls read as controls. |
| `--primary` | `#1F4E79` | Institutional blue. Primary actions, active nav. |
| `--accent` | `#A8752A` | Brass. Emphasis only, never a background wash. |
| `--ring` | `#1F4E79` | Focus, at full strength. Never a faint grey. |

### Dark

Not an inversion. A night ledger: warm off-white ink on a cool dark ground, with both accents lifted enough to hold contrast.

| Token | Hex | Role |
|---|---|---|
| `--background` | `#0E1319` | Page ground. |
| `--card` | `#151C24` | Raised surface. |
| `--foreground` | `#E8E6E1` | Body text, very slightly warm. |
| `--muted-foreground` | `#9AA4B0` | Secondary text. |
| `--border` | `#2A3542` | Rules. |
| `--input` | `#38455476` | Field edges. |
| `--primary` | `#6BA3D9` | Lifted blue, holds contrast on the dark ground. |
| `--accent` | `#D9A44C` | Lifted brass. |

### State scale

One ordered scale, used for performance bands, attendance warnings, application statuses and submission verdicts alike. Ordered by severity so it reads as a scale rather than a set of unrelated labels, and distinguishable without relying on hue alone.

| State | Light | Dark | Used by |
|---|---|---|---|
| At risk | `#9B2C2C` | `#E06C6C` | At-risk band, low attendance, rejected, wrong answer |
| Average | `#A8752A` | `#D9A44C` | Average band, under review, pending |
| Good | `#1F4E79` | `#6BA3D9` | Good band, shortlisted, in progress |
| Excellent | `#1E6A4F` | `#5CB894` | Excellent band, selected, accepted |

Every state also carries a text label or icon. Colour is never the only signal.

### Chart scale

Eight steps, derived from the same family, replacing all three ad-hoc palettes. Ordered so the first three are maximally separable, since most charts here use two or three series.

```
1  #1F4E79   blue        5  #9B2C2C   red
2  #1E6A4F   green       6  #2D7D8A   teal
3  #A8752A   brass       7  #7A6A4F   drab
4  #6B4E8C   plum        8  #4A5568   slate
```

These become the `--chart-1` … `--chart-8` tokens, and one exported module reads them so no chart ever hardcodes hex again.

**The database band colours change too.** `performance_bands.color_hex` currently holds `#16A34A`, `#2563EB`, `#CA8A04`, `#DC2626` — Tailwind defaults that would clash with this palette. They are configuration rows, not code, so they get updated to the state scale above through the existing admin screen. The fact that they are editable stays true.

---

## 4. Typography

Two families, clearly distinct in role.

- **Geist Variable** keeps every piece of user interface: labels, tables, buttons, body, and **all numbers**. It is already loaded and it has real tabular figures.
- **A text serif** takes page titles and the single headline number per view, and nothing else. It is what gives the record its character.

The one non-negotiable typographic rule: **every number gets `font-variant-numeric: tabular-nums`.** Marks, percentages, GPA, counts, dates, currency. Columns of figures that do not align are the single clearest sign that a data product was not designed, and this app is full of columns of figures.

Type scale, roughly a major third, with weights chosen per role rather than defaulting to bold everywhere:

| Role | Size | Family | Notes |
|---|---|---|---|
| Headline figure | 40–48px | Serif | One per view. The CGPA, the attendance percentage. |
| Page title | 24px | Serif | |
| Section heading | 16px, medium | Geist | |
| Body and table cells | 14px | Geist | Tabular figures on numeric cells. |
| Label and meta | 12px | Geist, muted | Sentence case, never all caps. |

Adding the serif means one new font dependency. If that is unwelcome, the fallback is a system serif stack, which costs nothing and still reads correctly on macOS and Windows.

---

## 5. Structure

Five rules, in priority order.

**1. Rules replace boxes.** Sections are separated by hairlines, not by a border-plus-radius-plus-shadow on every element. Cards keep a surface where elevation is genuinely meaningful — a dialog, a popover — and lose it where it was only decoration.

**2. Radius tightens.** From `0.625rem` to `0.375rem`. Small change, large effect: precise rather than friendly, which is what a record should be.

**3. One headline number per view.** On the student analytics page CGPA becomes the headline figure in serif, and attendance, marks and current GPA drop to a label-and-value row beneath it. This kills the four-identical-tiles pattern and creates the hierarchy that is missing today.

**4. Tables get the attention they deserve.** They are the primary interface of this product and are currently the least designed part of it. Numeric columns right-align with tabular figures, row height tightens, the header rule is heavier than the row rules, and a hover state makes row scanning easier.

**5. Role is signalled quietly.** A small label and a brass rule in the sidebar, not a tinted theme per role. Whole-theme role tinting was the alternative direction and was not the one chosen.

---

## 6. Dark mode

The tokens exist and the `dark:` variants exist. What is missing is the switch.

- A `ThemeProvider` writes the `dark` class on the root element, defaults to the system preference via `prefers-color-scheme`, and persists an explicit choice in `localStorage`.
- A toggle sits in the top bar beside the notification bell.
- The chart module reads its colours from CSS custom properties at render time so charts re-colour with the theme instead of freezing at their light values. This is the part most likely to be missed, because a chart built once with a hex string will silently stay light-themed on a dark ground.
- Every one of the 42 routes needs a look in both themes. Contrast is checked against WCAG AA, including the state scale on both grounds.

---

## 7. Defects to fix in the same pass

Two are already confirmed and both live in the components this work touches anyway.

**Every dropdown shows its raw value instead of the option label.** Verified in a browser: the student status filter reads `all` before selection and `PENDING` after choosing "Pending activation"; the academic year filter reads `__ALL__`; the faculty class picker reads `8`, the assignment id, while the card directly beneath it correctly reads "CSE301 — Data Structures and Algorithms". The cause is that Base UI's `Select.Value` renders the value unless the root is given an items map or the value is given a render function, and the shared wrapper in `components/ui/select.tsx` supplies neither. This affects all 78 dropdowns across 38 files, and it is one component to fix, not 78.

**Three pages log a Base UI warning** about a non-native element used where a native button was expected, which removes native button semantics. An accessibility fix, not a visual one.

Not a defect but worth doing while the admin screens are open: the student list is dominated by leftover test accounts from earlier verification sessions, with names like "Integrator Verify" and "Phase12 Admin Test". An evaluator sees them immediately.

---

## 8. Order of work

Sequenced so the highest-value, lowest-risk change lands first and each stage is verifiable on its own.

| Stage | Work | Risk |
|---|---|---|
| 1 | Rewrite the token block, light and dark. Add the serif, the radius change and tabular figures. | Low. Appearance changes everywhere, behaviour changes nowhere. |
| 2 | Add the chart colour module. Route all 31 hardcoded hex values through it. Update the four band rows. | Low. Mechanical, and the charts are isolated components. |
| 3 | Fix the dropdown label bug in the shared select wrapper. | Low, one component, but touches every form so it needs the full browser walk. |
| 4 | Add the theme provider and toggle. | Medium. Needs both-theme review across all 42 routes. |
| 5 | Rework the tables: alignment, figures, rules, hover. | Medium. Shared component, wide blast radius. |
| 6 | Rework the three dashboards and the analytics pages for hierarchy. | Medium. The most visible change and the most judgement-dependent. |

Stages 1 through 3 are worth doing even if the rest is deferred. Stage 2 alone fixes the worst problem in the product, which is that an analytics application ships five grey chart tokens and three competing palettes.

---

## 9. How it gets verified

The same way the rest of this project is verified, because a build passing is not evidence that a screen renders.

- `npm run build`, `npm test` and `npx oxlint` stay clean. The backend suite is untouched by this work but gets a run anyway.
- All 42 routes walked in a real browser in both themes, checking for console errors, failed requests and layout breaks.
- The four breakpoints re-checked at 1440, 1280, 768 and 390 pixels. Horizontal overflow must stay at zero, which it currently is.
- Contrast checked against WCAG AA for text and for the state scale on both grounds.
- Screenshots before and after for the pages an evaluator actually opens.

---

## 10. Explicitly not changing

- No API, DTO or backend behaviour. This is presentation only, with the single exception of the four editable band colour rows.
- No route changes, no navigation restructure.
- No new dependency beyond the serif family, and that one has a zero-cost fallback.
- Geist stays. Replacing the UI face would be change for its own sake.
