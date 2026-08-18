import { useState } from "react";
import { Button } from "@/components/ui/button";

const STACK = [
  "React 19 + Vite + TypeScript",
  "Tailwind CSS v4",
  "shadcn/ui",
  "react-router-dom, axios, chart.js (installed, unused until later phases)",
];

/**
 * Phase 1 placeholder.
 *
 * This is intentionally not a dashboard or a login screen — those are
 * fake functionality (scope §69) until the backend/auth work behind them
 * exists (Phase 2 onward). This page exists only to prove the build
 * pipeline — Vite, React, TypeScript, Tailwind CSS, shadcn/ui — is wired
 * up correctly. The button below performs a real (if small) action: it
 * toggles visibility of the stack list using local component state.
 */
function App() {
  const [showStack, setShowStack] = useState(false);

  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-background px-4 text-center text-foreground">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight">
          SmartCampus ERP
        </h1>
        <p className="max-w-md text-muted-foreground">
          Phase 1 — Foundation. The frontend build pipeline is wired up;
          real features (auth, dashboards, academics, and beyond) arrive in
          later phases.
        </p>
      </div>

      <Button onClick={() => setShowStack((v) => !v)}>
        {showStack ? "Hide" : "Show"} what's installed
      </Button>

      {showStack && (
        <ul className="space-y-1 text-sm text-muted-foreground">
          {STACK.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default App;
