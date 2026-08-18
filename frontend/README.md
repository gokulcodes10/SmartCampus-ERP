# SmartCampus ERP — Frontend

React + Vite + TypeScript, styled with Tailwind CSS v4 and shadcn/ui.

## Stack

- React 19, Vite, TypeScript
- Tailwind CSS v4 (`@tailwindcss/vite` plugin, CSS-first config in `src/index.css`)
- shadcn/ui (components generated into `src/components/ui/`)
- react-router-dom, axios, chart.js / react-chartjs-2 (installed; wired up
  starting Phase 2+ as those features are built)

## Directory layout (scope §3)

See the `README.md` in each `src/` subdirectory for what belongs there:
`components/`, `pages/`, `layouts/`, `services/`, `hooks/`, `context/`,
`utils/`, `routes/`, `assets/`.

## Setup

```bash
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL if the backend isn't on :8080
npm run dev             # http://localhost:5173, proxies /api to the backend
npm run build            # type-checks (tsc -b) then builds to dist/
```

## Adding shadcn/ui components

```bash
npx shadcn@latest add <component>
```

## Status

Phase 1 (Foundation) only — the build pipeline works and is proven by a
placeholder screen. No auth, dashboards, or domain features exist yet; see
`../PROJECT_PLAN.md` for the phase plan.
