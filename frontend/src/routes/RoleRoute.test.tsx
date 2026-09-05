import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import { RoleRoute } from "@/routes/RoleRoute";
import { useAuth } from "@/hooks/useAuth";
import type { AuthenticatedUser } from "@/types/auth";

vi.mock("@/hooks/useAuth", () => ({ useAuth: vi.fn() }));

function userOf(role: AuthenticatedUser["role"]): AuthenticatedUser {
  return {
    id: 1,
    email: "user@example.com",
    fullName: "Test User",
    role,
    enabled: true,
    createdAt: "2026-01-01T00:00:00Z",
  };
}

function renderRoleRoute() {
  return render(
    <MemoryRouter initialEntries={["/admin"]}>
      <Routes>
        <Route element={<RoleRoute allowedRoles={["ADMIN"]} />}>
          <Route path="/admin" element={<div>Admin only content</div>} />
        </Route>
        <Route path="/student" element={<div>Student dashboard</div>} />
        <Route path="/faculty" element={<div>Faculty dashboard</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("RoleRoute", () => {
  it("refuses a STUDENT hitting an ADMIN-only route, sending them to their own dashboard", () => {
    (useAuth as Mock).mockReturnValue({ user: userOf("STUDENT") });
    renderRoleRoute();
    expect(screen.queryByText("Admin only content")).not.toBeInTheDocument();
    expect(screen.getByText("Student dashboard")).toBeInTheDocument();
  });

  it("allows an ADMIN through to an ADMIN-only route", () => {
    (useAuth as Mock).mockReturnValue({ user: userOf("ADMIN") });
    renderRoleRoute();
    expect(screen.getByText("Admin only content")).toBeInTheDocument();
  });

  it("renders nothing when the user is not yet populated", () => {
    (useAuth as Mock).mockReturnValue({ user: null });
    const { container } = renderRoleRoute();
    expect(container).toHaveTextContent("");
  });
});
