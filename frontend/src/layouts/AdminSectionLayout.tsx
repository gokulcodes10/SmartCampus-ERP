import { Outlet } from "react-router-dom";

import { AdminNav } from "@/components/admin/AdminNav";

/** Wraps every /admin/* route with the Phase 3 academic-management sub-navigation. */
export function AdminSectionLayout() {
  return (
    <div className="mx-auto max-w-6xl space-y-4">
      <AdminNav />
      <Outlet />
    </div>
  );
}
