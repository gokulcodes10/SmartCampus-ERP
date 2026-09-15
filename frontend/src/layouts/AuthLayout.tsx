import { Outlet } from "react-router-dom";

/**
 * Shell for the public auth pages (login, register, forgot/verify/reset password) —
 * a centred card on the design's navy ground, carrying the wordmark above it.
 */
export function AuthLayout() {
  return (
    <div
      className="flex min-h-svh flex-col items-center justify-center gap-7 px-4 py-12"
      style={{ backgroundImage: "var(--gradient-kpi-navy)" }}
    >
      <div className="text-center">
        <h1 className="text-[26px] font-bold tracking-[0.02em] text-white">
          SMARTCAMPUS<span className="text-sidebar-ring">ERP</span>
        </h1>
        <p className="mt-1 text-[13px] text-sidebar-muted">Campus operations, end to end.</p>
      </div>
      <div className="w-full max-w-sm">
        <Outlet />
      </div>
    </div>
  );
}
