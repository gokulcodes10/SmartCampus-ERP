import { Link } from "react-router-dom";
import {
  BuildingIcon,
  GraduationCapIcon,
  LibraryBigIcon,
  UserRoundCheckIcon,
  UsersRoundIcon,
} from "lucide-react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";

/**
 * Deliberately minimal (scope §69 — no fake functionality). It shows the real
 * authenticated user from GET /api/auth/me plus real navigation into the Phase 3
 * academic-management screens (departments, courses, subjects, students, faculty) —
 * links, not data, so nothing here is a placeholder for numbers that don't exist yet.
 */
const MANAGEMENT_LINKS = [
  {
    to: "/admin/departments",
    label: "Departments",
    description: "Academic departments",
    icon: BuildingIcon,
  },
  {
    to: "/admin/courses",
    label: "Courses",
    description: "Programs per department",
    icon: LibraryBigIcon,
  },
  {
    to: "/admin/subjects",
    label: "Subjects",
    description: "Syllabus subjects per course",
    icon: GraduationCapIcon,
  },
  {
    to: "/admin/students",
    label: "Students",
    description: "Manage students, activate pending sign-ups (G1)",
    icon: UserRoundCheckIcon,
  },
  {
    to: "/admin/faculty",
    label: "Faculty",
    description: "Provision and manage staff accounts",
    icon: UsersRoundIcon,
  },
];

export default function AdminDashboardPage() {
  const { user } = useAuth();

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Welcome, {user?.fullName}</h1>
        <p className="text-muted-foreground">Admin dashboard</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Account</CardTitle>
          <CardDescription>Your authenticated account details.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-2 text-sm">
          <div className="flex justify-between border-b border-border py-2">
            <span className="text-muted-foreground">Email</span>
            <span>{user?.email}</span>
          </div>
          <div className="flex justify-between border-b border-border py-2">
            <span className="text-muted-foreground">Role</span>
            <span>{user?.role}</span>
          </div>
          <div className="flex justify-between py-2">
            <span className="text-muted-foreground">Account status</span>
            <span>{user?.enabled ? "Active" : "Disabled"}</span>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Academic administration</CardTitle>
          <CardDescription>
            Manage the core academic structure and accounts (Phase 3).
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-2 sm:grid-cols-2">
          {MANAGEMENT_LINKS.map(({ to, label, description, icon: Icon }) => (
            <Link
              key={to}
              to={to}
              className="flex items-start gap-3 rounded-lg border border-border p-3 text-sm transition-colors hover:bg-muted"
            >
              <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <span>
                <span className="block font-medium">{label}</span>
                <span className="block text-muted-foreground">{description}</span>
              </span>
            </Link>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
