import { describe, expect, it } from "vitest";

import { dashboardPathForRole } from "@/routes/dashboardPath";

describe("dashboardPathForRole", () => {
  it.each([
    ["STUDENT", "/student"],
    ["FACULTY", "/faculty"],
    ["ADMIN", "/admin"],
  ] as const)("maps %s to %s", (role, expected) => {
    expect(dashboardPathForRole(role)).toBe(expected);
  });
});
