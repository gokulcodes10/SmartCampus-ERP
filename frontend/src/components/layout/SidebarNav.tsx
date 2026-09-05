import { NavLink } from "react-router-dom";

import { cn } from "@/lib/utils";

export interface SidebarNavLink {
  to: string;
  label: string;
  end?: boolean;
}

interface SidebarNavProps {
  links: SidebarNavLink[];
  /** Called after a link is activated — used by the mobile drawer to close itself. */
  onNavigate?: () => void;
  className?: string;
}

/**
 * The link list shared by the persistent desktop sidebar and the mobile slide-over
 * drawer in `DashboardLayout`. Kept as one component so the two surfaces can never
 * drift out of sync with each other.
 */
export function SidebarNav({ links, onNavigate, className }: SidebarNavProps) {
  return (
    <nav className={cn("flex flex-col gap-1", className)}>
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          end={link.end}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              "flex min-h-9 items-center rounded-lg px-3 py-2 text-sm font-medium transition-colors",
              isActive
                ? "bg-sidebar-accent text-sidebar-accent-foreground"
                : "text-sidebar-foreground/80 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
            )
          }
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}
