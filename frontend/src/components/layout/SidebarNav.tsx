import type { LucideIcon } from "lucide-react";
import { CircleIcon } from "lucide-react";
import { NavLink } from "react-router-dom";

import { cn } from "@/lib/utils";

export interface SidebarNavLink {
  to: string;
  label: string;
  end?: boolean;
  /** Rendered at 16px ahead of the label; falls back to a neutral dot. */
  icon?: LucideIcon;
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
 *
 * Styling follows the design's navy rail: an active item is a solid indigo chip with
 * white type, an idle one is muted slate that lifts to white on hover.
 */
export function SidebarNav({ links, onNavigate, className }: SidebarNavProps) {
  return (
    <nav className={cn("flex flex-col gap-1.5", className)}>
      {links.map((link) => {
        const Icon = link.icon ?? CircleIcon;
        return (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                "flex min-h-10 items-center gap-3 rounded-[9px] px-3 py-2.5 text-[13.5px] transition-colors",
                isActive
                  ? "bg-sidebar-primary font-semibold text-sidebar-primary-foreground"
                  : "font-medium text-sidebar-accent hover:bg-white/10 hover:text-sidebar-foreground",
              )
            }
          >
            <Icon className="size-4 shrink-0 opacity-85" aria-hidden />
            <span className="truncate">{link.label}</span>
          </NavLink>
        );
      })}
    </nav>
  );
}
