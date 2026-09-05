# Design Definition Statement — SmartCampus ERP Website

A complete design system for the SmartCampus ERP marketing and showcase website. Written to be handed directly to Claude Design as the source of truth for building it.

This is **not** the design of the application itself. The product interface is a restrained working tool, planned separately in [`ui-design-plan.md`](ui-design-plan.md). This document describes the public site that introduces it: colourful, interactive, and built to be enjoyed on the way to being understood.

---

## 1. What this site is for

**The product.** SmartCampus ERP is a full-stack college management system for engineering institutions. It combines ordinary academic administration with an AI study assistant grounded in a student's real record, a sandboxed coding and contest platform, a placement portal with an automated eligibility engine, a resume builder with PDF export, interview preparation and scheduling, and real-time notifications.

**The audience, in priority order.**

1. **Evaluators and faculty reviewers** assessing whether this is real engineering or a mock-up. They are sceptical by default and they will look for the seams.
2. **Institutional decision-makers** wondering whether a system like this could run their college.
3. **Students and developers** curious about how it was built.

**The one job.** Convince a sceptic, in under a minute of scrolling, that every number in this system is real. That is the product's actual claim and its actual differentiator, so it is also the site's.

**The tone.** Confident and specific. It shows rather than asserts. It never says "revolutionary," "seamless," or "cutting-edge."

---

## 2. Design principles

**Colour is the map, not the decoration.** The product has seven modules. Each gets its own hue, used consistently everywhere that module appears — its section, its icon, its chart series, its navigation entry. A visitor learns the legend in the first screen and navigates by colour after that. This is what earns the word "colourful": the palette is doing structural work, not brightening things up.

**Show the real thing.** Every number, screenshot and example on this site comes from the running application. Real student records, real grade bands, real API responses. Invented statistics would contradict the exact claim the site is making.

**One spectacular moment, then discipline.** The hero earns a genuinely ambitious interaction. Everything after it is calm, so the hero keeps its power. Scattering effects across every section makes all of them feel cheap.

**Interaction over illustration.** Where a static image would explain something, prefer a small live thing the visitor can poke. The eligibility engine, the grade calculator and the notification push are all better as toys than as screenshots.

**Density is credibility.** This is an engineering product for engineers. Generous whitespace with three words per screen reads as marketing. Real tables, real figures and real code read as substance. Be spacious in layout and dense in content.

---

## 3. Colour system

### 3.1 The module spectrum

Seven hues, evenly distributed and held to a consistent perceptual lightness so no single one shouts over the others. This is the heart of the system.

| Module | Name | Hex | On dark | Stands for |
|---|---|---|---|---|
| Academics | Indigo | `#4F5BD5` | `#8B93F0` | Departments, courses, subjects, enrolment |
| Attendance & marks | Azure | `#1E88E5` | `#6FB8F5` | Registers, exams, grading |
| Analytics | Teal | `#00A39B` | `#4FD1C7` | GPA, trends, risk classification |
| AI assistant | Violet | `#8B4FD6` | `#C09BF0` | Study plans, explanations, practice questions |
| Coding | Amber | `#F0A422` | `#FFC65C` | Playground, problems, contests, leaderboards |
| Placement | Green | `#2FA84F` | `#6FD98A` | Companies, drives, eligibility, applications |
| Career | Coral | `#F0603C` | `#FF8F6F` | Resume builder, interview prep and scheduling |

**Rules.**
- A module's hue appears in its section, its icon, its chart series, and its nav entry. Nowhere else.
- Never place two module hues in the same component unless the component is explicitly the legend or the overview.
- Body text is never a module hue. Text is ink.
- The hue may tint a background at 6–10% opacity. It may not fill a large area at full strength except in the hero and the module cards.

### 3.2 Neutrals

| Token | Light | Dark | Role |
|---|---|---|---|
| `--ground` | `#FFFFFF` | `#0D1117` | Page background |
| `--ground-alt` | `#F5F7FB` | `#151C26` | Alternating section bands |
| `--ground-deep` | `#141A2E` | `#141A2E` | Full-bleed feature sections, both themes |
| `--surface` | `#FFFFFF` | `#1B2430` | Cards, panels |
| `--ink` | `#101828` | `#F0F3F8` | Primary text |
| `--ink-muted` | `#5A6478` | `#9BA6B8` | Secondary text, captions |
| `--rule` | `#E4E8F0` | `#2A3442` | Borders, dividers |

`--ground-deep` is deliberately the same value in both themes. Those sections are always dark, which gives the page a rhythm of light and dark bands independent of the visitor's theme choice.

### 3.3 Semantic colour

Reserved for state. Never used decoratively, so that when one appears it means something.

| State | Light | Dark |
|---|---|---|
| Positive, eligible, accepted | `#1E8E4A` | `#5CC97F` |
| Caution, pending, under review | `#B8791B` | `#E0A845` |
| Negative, ineligible, rejected | `#C0392B` | `#F07568` |
| Informational | `#1E88E5` | `#6FB8F5` |

Semantic colour is always paired with a label or an icon. Colour is never the only carrier of meaning.

### 3.4 Gradients

Permitted in exactly three places, and always between **adjacent hues in the spectrum**, never between arbitrary colours.

- The hero backdrop: a slow multi-stop wash across the full spectrum at low saturation.
- Module card headers: a two-stop gradient from the module hue to its neighbour, at 135 degrees.
- The section that introduces the module legend: the full spectrum as a single continuous bar.

A gradient behind body text is not permitted anywhere.

### 3.5 Contrast

Every text and background pairing meets WCAG AA: 4.5:1 for body text, 3:1 for large text and interface controls. The module hues at full strength are used for large text and graphics only. For body-sized text on a light ground, use the ink colours, not a module hue.

---

## 4. Typography

Two families, distinct in role, neither of them a default reach.

**Display — a geometric or grotesque sans with real personality.** Suggested: **Bricolage Grotesque** or **Instrument Sans**. Used for the hero statement, section headings and headline figures. Set tight: `-0.02em` tracking at large sizes, line height 0.95 to 1.05 for anything above 40px.

**Text and data — Inter or Geist.** Everything else: body, labels, tables, code annotations, figures. Both have true tabular figures, which matters because this site shows a lot of columns of numbers.

**Monospace — JetBrains Mono.** API responses, code snippets and the terminal-style moments only. Not for labels, which is a common tell.

### Scale

| Role | Size | Line height | Weight | Family |
|---|---|---|---|---|
| Hero statement | 64–96px fluid | 0.95 | 500 | Display |
| Section heading | 40px | 1.1 | 500 | Display |
| Subsection | 24px | 1.25 | 500 | Display |
| Lead paragraph | 20px | 1.5 | 400 | Text |
| Body | 17px | 1.6 | 400 | Text |
| Small, caption | 14px | 1.5 | 400 | Text |
| Data and figures | Varies | 1.2 | 500 | Text, tabular figures |

**Rules.** Sentence case everywhere, including buttons and navigation. No all-caps labels. Body line length stays under 75 characters. Do not accent a single word in a heading with colour or italics — if a heading needs emphasis, rewrite the heading.

---

## 5. Layout, spacing and form

**Grid.** 12 columns, 1240px maximum content width, 32px gutters. Full-bleed sections break the container deliberately, not accidentally.

**Spacing scale.** 4, 8, 12, 16, 24, 32, 48, 64, 96, 128. Nothing between.

**Section rhythm.** 128px vertical padding on desktop, 80px on tablet, 56px on mobile. Alternate `--ground`, `--ground-alt` and `--ground-deep` so the page has a visible pulse as you scroll.

**Radius.** 4px for controls and small elements, 12px for cards, 24px for large feature panels, and full for pills. Three distinct values, applied by size of element — not one radius on everything, which is the flattest signal that a page was assembled from a kit.

**Elevation.** Two levels only. Resting cards use a 1px rule and no shadow. Lifted elements — dropdowns, dialogs, hovering cards — use `0 8px 24px rgba(16, 24, 40, 0.10)`. There is no third level and no glow.

**Borders.** 1px, `--rule`. A module card may use a 2px top border in its own hue as its only chrome.

---

## 6. Components

Every interactive element defines five states: rest, hover, focus-visible, active, disabled. Focus is always visible and always a 2px ring in the module or primary hue with a 2px offset. Never remove the outline.

**Primary button.** Solid ink background, white text, 12px by 24px padding, 4px radius. Hover lifts 1px and darkens 6%. Active returns to 0px. No arrow appended to the label. The label is a verb describing exactly what happens.

**Secondary button.** Transparent with a 1px rule. Hover fills with the module hue at 8%.

**Module card.** Surface background, 12px radius, 2px top border in the module hue, an icon in that hue, a heading, two lines of description, and a real figure from the product. Hover raises it 2px and deepens the top border. The whole card is a link, not a card containing a link.

**Stat block.** A large figure in display weight with tabular figures, a short label beneath, and a one-line source note. Every figure on this site carries its provenance, for example "measured across 26 seeded students."

**Chart.** Series colours come from the module spectrum in order. Axes and gridlines use `--rule`. No 3D, no drop shadows on data, no more than six series. Charts animate once on entry and never loop.

**Table.** Header row with a 2px bottom rule, body rows with 1px rules, numeric columns right-aligned with tabular figures, and a hover tint at 4% of the section hue.

**Code and API panel.** `--ground-deep` background, JetBrains Mono at 14px, syntax colours drawn from the spectrum: keys in azure, strings in green, numbers in amber, comments in muted ink. A copy control in the corner.

**Navigation.** Sticky, transparent over the hero and gaining a background and rule after 80px of scroll. The seven module links each show a small dot in their own hue.

---

## 7. Motion and interaction

**The budget.** One large orchestrated moment in the hero. After that, motion responds to what the visitor does and nothing moves on its own.

**Timing.** 150ms for state changes, 300ms for entrances, 600ms for the hero sequence. Easing `cubic-bezier(0.22, 1, 0.36, 1)` for entrances, `ease-out` for state changes.

**Respect `prefers-reduced-motion`.** All entrance and hero animation resolves immediately to its final state. Nothing important is conveyed by motion alone.

### The four interactive moments

These are the site. Everything else supports them.

**1. The hero — "a record becomes an insight."**
A student record materialises as a set of raw figures: attendance 95.00%, two subjects at 91.50%, grade O, seven credits. Over 600ms the figures reorganise themselves into the assistant's answer, in the assistant's own words, citing those same numbers. The visitor sees data turn into advice, which is the product's central idea, expressed once and never repeated.

**2. The eligibility engine.**
A live control. The visitor drags a CGPA slider and watches a placement drive flip between eligible and ineligible, with the refusal naming the exact rule and the exact gap: "This drive requires a minimum CGPA of 7.50. Yours is 7.00." Use the real message text from the running product. This is the most persuasive interaction available, because it demonstrates a real rule engine rather than describing one.

**3. The grade band editor.**
A miniature of the admin screen. Drag a band threshold and watch already-computed grades change in a table beside it. It proves configuration over hard-coding in about four seconds.

**4. The live notification.**
Two panes side by side, an administrator and a student. Publish an announcement in one and it appears in the other with no reload, over a real socket. It mirrors the product's own Phase 11 checkpoint.

---

## 8. Page structure

```
┌─────────────────────────────────────────────────────────┐
│  NAV — transparent, seven module dots                   │
├─────────────────────────────────────────────────────────┤
│  HERO                                    ground-deep    │
│  Statement + the record-becomes-insight animation       │
│  Spectrum wash behind, low saturation                   │
├─────────────────────────────────────────────────────────┤
│  PROOF STRIP                                  ground    │
│  Four real figures with sources. No invented metrics.   │
├─────────────────────────────────────────────────────────┤
│  THE LEGEND                               ground-alt    │
│  Seven modules as a spectrum bar. Teaches the colour    │
│  language the rest of the page uses.                    │
├─────────────────────────────────────────────────────────┤
│  MODULE SECTIONS  x7           alternating grounds      │
│  Each in its own hue: what it does, a real screenshot,  │
│  one real figure, one honest limitation where it has    │
│  one.                                                   │
├─────────────────────────────────────────────────────────┤
│  ELIGIBILITY TOY                         ground-deep    │
│  The interactive slider. Green section.                 │
├─────────────────────────────────────────────────────────┤
│  GROUNDED AI                                  ground    │
│  Side by side: the real record, and the prompt built    │
│  from it. Violet section.                               │
├─────────────────────────────────────────────────────────┤
│  THREE ROLES                              ground-alt    │
│  Student, faculty, admin. What each sees, and the       │
│  permission boundary between them.                      │
├─────────────────────────────────────────────────────────┤
│  BUILT HONESTLY                          ground-deep    │
│  The differentiator: what does not work and why.        │
│  Code execution is blocked on this hardware. Said       │
│  plainly. This section earns more trust than any other. │
├─────────────────────────────────────────────────────────┤
│  ARCHITECTURE                                 ground    │
│  Stack, layering, test counts, migration strategy.      │
│  For the technical reader who scrolled this far.        │
├─────────────────────────────────────────────────────────┤
│  CLOSE + FOOTER                                         │
└─────────────────────────────────────────────────────────┘
```

The "Built honestly" section is not a disclaimer and should not read as one. A product that names its own limits is more believable than one that claims none, and this site's whole argument is believability.

---

## 9. Voice and copy

**Rules.** Sentence case. Active voice. Specific numbers over adjectives. A verb in every button that names the outcome. No exclamation marks. Never "seamless," "powerful," "revolutionary," "leverage," or "unlock."

**Examples, in the right register:**

- Hero: "Every number in this system came from a database. None of them were invented."
- Analytics: "GPA is credit-weighted across seven graded credits. Change a grade band and it recalculates."
- AI: "The assistant reads the student's actual marks and attendance before it answers. Ask it what to revise and it names the subject, with the percentage."
- Placement: "Ineligible students are told which rule they missed, and by how much."
- Coding, on the limitation: "Code execution needs a sandbox this hardware cannot provide. Submissions record the failure honestly rather than inventing a verdict."

**Empty and error states** state what happened and what to do. They do not apologise and they are never vague.

---

## 10. Accessibility

- WCAG AA contrast on every pairing, verified rather than assumed.
- Keyboard reachable throughout, in a logical order, with a visible focus ring at all times.
- Every interactive toy is operable by keyboard. The CGPA slider is a real range input.
- `prefers-reduced-motion` honoured across all animation.
- Semantic landmarks and one `h1`, with heading levels never skipped.
- Alt text on every screenshot describing what the screen shows, not "screenshot."
- Colour never the sole carrier of meaning. Every state carries a label.
- Targets at least 44 by 44 pixels on touch.

---

## 11. Responsive behaviour

| Breakpoint | Width | Behaviour |
|---|---|---|
| Mobile | to 640px | Single column. Hero animation simplifies to two steps. Module cards stack. Tables scroll inside their own container, never the page. |
| Tablet | 641–1024px | Two columns. Navigation collapses to a drawer. Interactive toys keep full function. |
| Desktop | 1025–1440px | Full 12-column layout. |
| Wide | 1441px+ | Content capped at 1240px, background treatments run full bleed. |

Horizontal page overflow must be zero at every width. Wide content scrolls inside its own container.

---

## 12. Notes for the build

- Define every colour as a CSS custom property on `:root`, with the dark values under both a `prefers-color-scheme` query and an explicit `[data-theme]` attribute, so a toggle can override the system preference in both directions.
- Give `body` an explicit background. A transparent body inherits whatever sits behind it.
- The module spectrum should exist as an array in one place, so charts, icons, nav dots and section themes all read from the same source and cannot drift apart.
- Build the hero animation so it also reads correctly as a static frame, because it will be screenshotted and it must survive reduced-motion.
- Use real screenshots from the running application at `localhost:5175`. Placeholder imagery would undercut the entire argument.

## 13. Do not

- No cream ground with a terracotta accent, and no near-black ground with a single acid-green accent. Both are the current default look and both will read as generic.
- No all-caps tracked-out labels above headings.
- No arrows appended to button or link text.
- No identical rounded cards with the same soft grey shadow for every kind of content.
- No numbered markers on content that is not actually a sequence.
- No invented statistics, no fake testimonials, no logos of institutions that are not using this.
- No autoplaying carousel, and no element that moves without the visitor doing something.
- No gradient behind body text.
