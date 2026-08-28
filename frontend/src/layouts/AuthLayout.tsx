import { Outlet } from "react-router-dom";

/** Shell for the public auth pages (login, register, forgot/verify/reset password) — centered card, no nav. */
export function AuthLayout() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-6 bg-background px-4 py-12">
      <h1 className="text-2xl font-semibold tracking-tight">SmartCampus ERP</h1>
      <div className="w-full max-w-sm">
        <Outlet />
      </div>
    </div>
  );
}
