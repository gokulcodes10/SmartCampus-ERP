import { Navigate } from "react-router-dom";

import { dashboardPathForRole } from "@/routes/dashboardPath";
import { useAuth } from "@/hooks/useAuth";

/** `/` never renders content itself — it forwards to the login page or the user's dashboard. */
export function RootRedirect() {
  const { isAuthenticated, isLoading, user } = useAuth();

  if (isLoading) {
    return null;
  }

  if (isAuthenticated && user) {
    return <Navigate to={dashboardPathForRole(user.role)} replace />;
  }

  return <Navigate to="/login" replace />;
}
