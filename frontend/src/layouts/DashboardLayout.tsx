import { Outlet } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";

/** Shell for authenticated pages — a top bar with identity + logout, then the routed page. */
export function DashboardLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="flex items-center justify-between border-b border-border px-4 py-3 sm:px-6">
        <span className="text-sm font-semibold tracking-tight">SmartCampus ERP</span>
        <div className="flex items-center gap-3">
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
      <main className="flex-1 px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
