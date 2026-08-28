import type { Role } from "@/types/auth";

/** Where a just-authenticated (or root-path) user of this role lands. */
export function dashboardPathForRole(role: Role): string {
  switch (role) {
    case "STUDENT":
      return "/student";
    case "FACULTY":
      return "/faculty";
    case "ADMIN":
      return "/admin";
    default:
      return "/login";
  }
}
