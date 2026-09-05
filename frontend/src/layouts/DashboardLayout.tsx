import { useMemo, useRef, useState } from "react";
import { MenuIcon } from "lucide-react";
import { Outlet } from "react-router-dom";

import { MobileNavDrawer } from "@/components/layout/MobileNavDrawer";
import { SidebarNav, type SidebarNavLink } from "@/components/layout/SidebarNav";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import type { Role } from "@/types/auth";

const CODING_LINKS: SidebarNavLink[] = [
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
const STUDENT_AI_LINKS: SidebarNavLink[] = [
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

/**
 * Shell for authenticated pages: a persistent sidebar at `lg` and above, a
 * hamburger-triggered slide-over drawer below it, a top bar carrying
 * notifications/identity/logout, and the routed page in the main region.
 */
export function DashboardLayout() {
  const { user, logout } = useAuth();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const menuTriggerRef = useRef<HTMLButtonElement>(null);

  // The drawer isn't opened through Base UI's own Dialog.Trigger (the button also
  // needs to render only below `lg`), so focus-return on close — via Escape, an
  // outside press, or the in-drawer close button — is handled here instead of
  // relying on the primitive's built-in trigger tracking.
  function handleMobileNavOpenChange(open: boolean) {
    setMobileNavOpen(open);
    if (!open) menuTriggerRef.current?.focus();
  }

  const links = useMemo<SidebarNavLink[]>(() => {
    if (!user) return [];
    return [
      { to: ANALYTICS_PATH_BY_ROLE[user.role], label: "Analytics" },
      { to: "/announcements", label: "Announcements" },
      ...CODING_LINKS,
      ...(user.role === "STUDENT" ? STUDENT_AI_LINKS : []),
    ];
  }, [user]);

  return (
    <div className="flex min-h-svh bg-background">
      {user && (
        <aside className="sticky top-0 hidden h-svh w-64 shrink-0 flex-col overflow-y-auto border-r border-sidebar-border bg-sidebar px-3 py-4 text-sidebar-foreground lg:flex">
          <div className="px-2 pb-4 text-sm font-semibold tracking-tight">SmartCampus ERP</div>
          <SidebarNav links={links} />
        </aside>
      )}

      {user && <MobileNavDrawer open={mobileNavOpen} onOpenChange={handleMobileNavOpenChange} links={links} />}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-b border-border px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2">
            {user && (
              <Button
                ref={menuTriggerRef}
                variant="ghost"
                size="icon"
                className="lg:hidden"
                aria-label="Open navigation menu"
                onClick={() => setMobileNavOpen(true)}
              >
                <MenuIcon />
              </Button>
            )}
            <span className="text-sm font-semibold tracking-tight">SmartCampus ERP</span>
          </div>
          <div className="flex flex-wrap items-center gap-2 sm:gap-3">
            {user && <NotificationBell />}
            {user && (
              <span className="text-sm text-muted-foreground">
                {user.fullName} <span className="text-xs">({user.role})</span>
              </span>
            )}
            <Button variant="outline" size="default" onClick={logout}>
              Log out
            </Button>
          </div>
        </header>
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
