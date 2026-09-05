import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import { ProtectedRoute } from "@/routes/ProtectedRoute";
import { useAuth } from "@/hooks/useAuth";

vi.mock("@/hooks/useAuth", () => ({ useAuth: vi.fn() }));

/** Reads the `from` location react-router hands to /login and renders its pathname. */
function LoginProbe() {
  const location = useLocation();
  const from = (location.state as { from?: { pathname: string } } | null)?.from;
  return <div>login page, from: {from?.pathname ?? "none"}</div>;
}

function renderProtected(initialPath: string) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/admin/students" element={<div>Admin students page</div>} />
        </Route>
        <Route path="/login" element={<LoginProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  it("shows a loading state while auth is bootstrapping", () => {
    (useAuth as Mock).mockReturnValue({ isAuthenticated: false, isLoading: true });
    renderProtected("/admin/students");
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("redirects an unauthenticated visit to /login, preserving the intended location", () => {
    (useAuth as Mock).mockReturnValue({ isAuthenticated: false, isLoading: false });
    renderProtected("/admin/students");
    expect(screen.getByText("login page, from: /admin/students")).toBeInTheDocument();
  });

  it("renders the protected content once authenticated", () => {
    (useAuth as Mock).mockReturnValue({ isAuthenticated: true, isLoading: false });
    renderProtected("/admin/students");
    expect(screen.getByText("Admin students page")).toBeInTheDocument();
  });
});
