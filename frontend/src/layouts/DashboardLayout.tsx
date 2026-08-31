import { NavLink, Outlet } from "react-router-dom";

import { NotificationBell } from "@/components/notifications/NotificationBell";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import { cn } from "@/lib/utils";
import type { Role } from "@/types/auth";

const CODING_LINKS = [
  { to: "/coding", label: "Coding", end: true },
  { to: "/coding/submissions", label: "Submissions" },
  { to: "/coding/contests", label: "Contests" },
  { to: "/coding/leaderboard", label: "Leaderboard" },
];

/**
 * STUDENT-only links — unlike CODING_LINKS above. Phase 6 AI assistant, Phase 8
 * placement (job browsing + the caller's own applications) and Phase 10 interview
 * prep/scheduling all live here since they render only for user.role === "STUDENT".
 */
const STUDENT_AI_LINKS = [
  { to: "/student/ai", label: "AI Assistant" },
  { to: "/student/study-plans", label: "Study Plans" },
  { to: "/student/jobs", label: "Placements" },
  { to: "/student/applications", label: "My Applications" },
  { to: "/student/resumes", label: "Resume" },
  { to: "/student/interview-prep", label: "Interview Prep" },
  { to: "/student/interviews", label: "Interviews" },
];

/** The Phase 5 analytics dashboard lives at a different path per role. */
const ANALYTICS_PATH_BY_ROLE: Record<Role, string> = {
  STUDENT: "/student/analytics",
  FACULTY: "/faculty/analytics",
  ADMIN: "/admin/analytics",
};

/** Shell for authenticated pages — a top bar with identity + logout, then the routed page. */
export function DashboardLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="flex items-center justify-between border-b border-border px-4 py-3 sm:px-6">
        <span className="text-sm font-semibold tracking-tight">SmartCampus ERP</span>
        <div className="flex items-center gap-3">
          {user && <NotificationBell />}
          {user && (
            <span className="text-sm text-muted-foreground">
              {user.fullName} <span className="text-xs">({user.role})</span>
            </span>
          )}
          <Button variant="outline" size="sm" onClick={logout}>
            Log out
          </Button>
        </div>
      </header>
      {user && (
        <nav className="flex flex-wrap gap-1 border-b border-border px-4 py-2 sm:px-6">
          <NavLink
            to={ANALYTICS_PATH_BY_ROLE[user.role]}
            className={({ isActive }) =>
              cn(
                "rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground",
              )
            }
          >
            Analytics
          </NavLink>
          <NavLink
            to="/announcements"
            className={({ isActive }) =>
              cn(
                "rounded-lg px-3 py-1.5 text-sm font-medium transition-colors",
                isActive
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground",
              )
            }
          >
            Announcements
          </NavLink>
          {CODING_LINKS.map((link) => (
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
          {user.role === "STUDENT" &&
            STUDENT_AI_LINKS.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
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
      )}
      <main className="flex-1 px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
