import { Navigate, Outlet } from "react-router-dom";

import { dashboardPathForRole } from "@/routes/dashboardPath";
import { useAuth } from "@/hooks/useAuth";
import type { Role } from "@/types/auth";

/**
 * Gates its child routes to the given roles. Must be nested under
 * `<ProtectedRoute>` (it assumes `user` is already populated); a role
 * mismatch redirects to that user's own dashboard rather than showing an
 * error, since the user IS authenticated — they just don't belong here.
 */
export function RoleRoute({ allowedRoles }: { allowedRoles: Role[] }) {
  const { user } = useAuth();

  if (!user) {
    return null;
  }

  if (!allowedRoles.includes(user.role)) {
    return <Navigate to={dashboardPathForRole(user.role)} replace />;
  }

  return <Outlet />;
}
