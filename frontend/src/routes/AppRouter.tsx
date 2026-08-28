import { BrowserRouter, Route, Routes } from "react-router-dom";

import { AuthProvider } from "@/context/AuthContext";
import { AuthLayout } from "@/layouts/AuthLayout";
import { DashboardLayout } from "@/layouts/DashboardLayout";
import ForgotPasswordPage from "@/pages/auth/ForgotPasswordPage";
import LoginPage from "@/pages/auth/LoginPage";
import RegisterPage from "@/pages/auth/RegisterPage";
import ResetPasswordPage from "@/pages/auth/ResetPasswordPage";
import VerifyOtpPage from "@/pages/auth/VerifyOtpPage";
import AdminDashboardPage from "@/pages/dashboard/AdminDashboardPage";
import FacultyDashboardPage from "@/pages/dashboard/FacultyDashboardPage";
import StudentDashboardPage from "@/pages/dashboard/StudentDashboardPage";
import NotFoundPage from "@/pages/NotFoundPage";
import { ProtectedRoute } from "@/routes/ProtectedRoute";
import { RoleRoute } from "@/routes/RoleRoute";
import { RootRedirect } from "@/routes/RootRedirect";

/**
 * The Phase 2 route tree: authentication plus the three role dashboards.
 * Self-contained — owns its own `BrowserRouter` and `AuthProvider` — so
 * `App.tsx` (shared, integrator-owned) only needs to render `<AppRouter />`.
 */
export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<AuthLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
            <Route path="/verify-otp" element={<VerifyOtpPage />} />
            <Route path="/reset-password" element={<ResetPasswordPage />} />
          </Route>

          <Route element={<ProtectedRoute />}>
            <Route element={<DashboardLayout />}>
              <Route element={<RoleRoute allowedRoles={["STUDENT"]} />}>
                <Route path="/student" element={<StudentDashboardPage />} />
              </Route>
              <Route element={<RoleRoute allowedRoles={["FACULTY"]} />}>
                <Route path="/faculty" element={<FacultyDashboardPage />} />
              </Route>
              <Route element={<RoleRoute allowedRoles={["ADMIN"]} />}>
                <Route path="/admin" element={<AdminDashboardPage />} />
              </Route>
            </Route>
          </Route>

          <Route path="/" element={<RootRedirect />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default AppRouter;
