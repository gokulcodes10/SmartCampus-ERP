import path from "node:path";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

/**
 * Standalone Vitest config (Phase 12, scope §64 "Frontend testing").
 *
 * Deliberately NOT merged into vite.config.ts (that file is owned by another
 * agent this wave) — this duplicates only what the test runner needs: the
 * same `@` -> ./src alias vite.config.ts resolves via
 * `path.resolve(import.meta.dirname, './src')`, plus jsdom + the RTL setup
 * file. `test.css: false` stubs any stray CSS import (e.g. a page importing
 * index.css) instead of running it through the Tailwind pipeline, since
 * jsdom never paints and no test asserts on computed styles.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    css: false,
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
    },
  },
});
