import { useMemo, useRef, useState } from "react";
import {
  BarChart3Icon,
  BookOpenCheckIcon,
  BriefcaseIcon,
  CalendarCheckIcon,
  ClipboardCheckIcon,
  CodeIcon,
  FileTextIcon,
  GraduationCapIcon,
  LayoutDashboardIcon,
  LifeBuoyIcon,
  ListChecksIcon,
  MegaphoneIcon,
  MenuIcon,
  MessagesSquareIcon,
  SearchIcon,
  SendIcon,
  SparklesIcon,
  TrophyIcon,
} from "lucide-react";
import { Outlet } from "react-router-dom";

import { MobileNavDrawer } from "@/components/layout/MobileNavDrawer";
import { SidebarNav, type SidebarNavLink } from "@/components/layout/SidebarNav";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import { dashboardPathForRole } from "@/routes/dashboardPath";
import type { Role } from "@/types/auth";

const CODING_LINKS: SidebarNavLink[] = [
  { to: "/coding", label: "Coding", end: true, icon: CodeIcon },
  { to: "/coding/submissions", label: "Submissions", icon: SendIcon },
  { to: "/coding/contests", label: "Contests", icon: TrophyIcon },
  { to: "/coding/leaderboard", label: "Leaderboard", icon: ListChecksIcon },
];

/**
 * STUDENT-only links — unlike CODING_LINKS above. Phase 6 AI assistant, Phase 8
 * placement (job browsing + the caller's own applications) and Phase 10 interview
 * prep/scheduling all live here since they render only for user.role === "STUDENT".
 */
const FACULTY_LINKS: SidebarNavLink[] = [
  { to: "/faculty/attendance", label: "Attendance", icon: ClipboardCheckIcon },
  { to: "/faculty/exams", label: "Exams", icon: BookOpenCheckIcon },
  { to: "/faculty/marks", label: "Marks", icon: GraduationCapIcon },
  { to: "/faculty/announcements", label: "My Announcements", icon: MegaphoneIcon },
];

const STUDENT_AI_LINKS: SidebarNavLink[] = [
  { to: "/student/ai", label: "AI Assistant", icon: SparklesIcon },
  { to: "/student/study-plans", label: "Study Plans", icon: BookOpenCheckIcon },
  { to: "/student/jobs", label: "Placements", icon: BriefcaseIcon },
  { to: "/student/applications", label: "My Applications", icon: ClipboardCheckIcon },
  { to: "/student/resumes", label: "Resume", icon: FileTextIcon },
  { to: "/student/interview-prep", label: "Interview Prep", icon: MessagesSquareIcon },
  { to: "/student/interviews", label: "Interviews", icon: CalendarCheckIcon },
];

/** The Phase 5 analytics dashboard lives at a different path per role. */
const ANALYTICS_PATH_BY_ROLE: Record<Role, string> = {
  STUDENT: "/student/analytics",
  FACULTY: "/faculty/analytics",
  ADMIN: "/admin/analytics",
};

const ROLE_LABEL: Record<Role, string> = {
  STUDENT: "Student",
  FACULTY: "Faculty",
  ADMIN: "Administrator",
};

/** Titles are not part of a person's initials — "Dr. Ramesh Iyer" is RI, not DI. */
const HONORIFICS = new Set(["dr", "mr", "mrs", "ms", "miss", "prof", "shri", "smt"]);

/** "Asha Menon" → "AM"; a single-word name yields its first two letters. */
function initialsOf(fullName: string): string {
  const parts = fullName
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .filter((part) => !HONORIFICS.has(part.replace(/\./g, "").toLowerCase()));
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/**
 * Shell for authenticated pages: a persistent navy sidebar at `lg` and above, a
 * hamburger-triggered slide-over drawer below it, a white top bar carrying search,
 * notifications and identity, and the routed page on the grey-blue canvas.
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
      { to: dashboardPathForRole(user.role), label: "Dashboard", end: true, icon: LayoutDashboardIcon },
      { to: ANALYTICS_PATH_BY_ROLE[user.role], label: "Analytics", icon: BarChart3Icon },
      { to: "/announcements", label: "Announcements", icon: MegaphoneIcon },
      ...CODING_LINKS,
      ...(user.role === "STUDENT" ? STUDENT_AI_LINKS : []),
      ...(user.role === "FACULTY" ? FACULTY_LINKS : []),
    ];
  }, [user]);

  return (
    <div className="flex min-h-svh bg-background">
      {user && (
        <aside className="sticky top-0 hidden h-svh w-60 shrink-0 flex-col overflow-y-auto bg-sidebar px-4 py-5 text-sidebar-foreground lg:flex">
          <div className="px-2.5 pt-1 pb-3.5 text-[19px] font-bold tracking-[0.02em]">
            SMARTCAMPUS<span className="text-sidebar-ring">ERP</span>
          </div>

          <div className="mb-3.5 flex items-center gap-2.5 rounded-[10px] bg-white/10 px-3 py-2.5">
            <span className="grid size-9 shrink-0 place-items-center rounded-full bg-sidebar-primary text-xs font-bold text-sidebar-primary-foreground">
              {initialsOf(user.fullName)}
            </span>
            <span className="min-w-0 leading-tight">
              <span className="block truncate text-[13px] font-semibold">{user.fullName}</span>
              <span className="block truncate text-[11px] text-sidebar-muted">{ROLE_LABEL[user.role]}</span>
            </span>
          </div>

          <SidebarNav links={links} />

          <div className="flex-1" />

          <div className="flex items-center gap-2.5 px-3 py-2.5">
            <span className="grid size-9 shrink-0 place-items-center rounded-full bg-white text-sidebar">
              <LifeBuoyIcon className="size-4" aria-hidden />
            </span>
            <span className="min-w-0 leading-tight">
              <span className="block text-[12.5px] font-semibold">Need help?</span>
              <span className="block truncate text-[11px] text-sidebar-muted">Contact the campus IT cell</span>
            </span>
          </div>
        </aside>
      )}

      {user && <MobileNavDrawer open={mobileNavOpen} onOpenChange={handleMobileNavOpenChange} links={links} />}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-border bg-card px-4 py-3 sm:px-8">
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
            <span className="text-sm font-bold tracking-tight lg:hidden">
              SMARTCAMPUS<span className="text-primary">ERP</span>
            </span>
          </div>

          {/* The design's search affordance. Wired to nothing yet, so it is not
              presented as a working control — it reads as the page's own label. */}
          {user && (
            <div className="hidden min-w-0 flex-1 items-center gap-2 sm:flex">
              <span className="flex min-w-0 max-w-[520px] flex-1 items-center gap-2 rounded-[10px] bg-muted px-4 py-2.5 text-[13px] text-muted-foreground">
                <SearchIcon className="size-4 shrink-0" aria-hidden />
                <span className="truncate">Search students, subjects, jobs…</span>
              </span>
            </div>
          )}

          <div className="flex flex-1 flex-wrap items-center justify-end gap-2 sm:gap-2.5">
            {user && <NotificationBell />}
            {user && (
              <span className="hidden text-right leading-tight sm:block">
                <span className="block text-[13px] font-semibold">{user.fullName}</span>
                <span className="block text-[11px] text-muted-foreground">{ROLE_LABEL[user.role]}</span>
              </span>
            )}
            {user && (
              <span className="grid size-9 shrink-0 place-items-center rounded-[10px] bg-primary text-xs font-bold text-primary-foreground">
                {initialsOf(user.fullName)}
              </span>
            )}
            <Button variant="outline" size="default" onClick={logout}>
              Log out
            </Button>
          </div>
        </header>
        <main className="min-w-0 flex-1 px-4 py-7 sm:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
