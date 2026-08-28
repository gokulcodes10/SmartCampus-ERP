import { NavLink } from "react-router-dom";

import { cn } from "@/lib/utils";

const LINKS = [
  { to: "/admin", label: "Overview", end: true },
  { to: "/admin/departments", label: "Departments" },
  { to: "/admin/courses", label: "Courses" },
  { to: "/admin/subjects", label: "Subjects" },
  { to: "/admin/students", label: "Students" },
  { to: "/admin/faculty", label: "Faculty" },
];

/** Sub-navigation across the Phase 3 admin academic-management screens. */
export function AdminNav() {
  return (
    <nav className="flex flex-wrap gap-1 border-b border-border pb-3">
      {LINKS.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          end={link.end}
          className={({ isActive }) =>
            cn(
              "rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
              isActive
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )
          }
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}
