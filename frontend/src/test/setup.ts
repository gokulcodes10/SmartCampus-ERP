import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// React Testing Library does not auto-cleanup outside of a test framework
// it recognizes as Jest; wire it explicitly for Vitest so each test starts
// from an empty document body.
afterEach(() => {
  cleanup();
});
