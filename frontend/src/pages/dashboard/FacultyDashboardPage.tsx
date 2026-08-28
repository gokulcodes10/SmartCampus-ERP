import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";

/**
 * Deliberately minimal (scope §69 — no fake functionality). It shows the
 * real authenticated user from GET /api/auth/me and nothing else: subject
 * assignments, attendance/marks entry and student rosters don't exist until
 * Phase 3/4, so no list, chart, or button stands in for them here.
 */
export default function FacultyDashboardPage() {
  const { user } = useAuth();

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Welcome, {user?.fullName}</h1>
        <p className="text-muted-foreground">Faculty dashboard</p>
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
          <CardTitle>Teaching</CardTitle>
          <CardDescription>
            Subject assignments and attendance/marks entry arrive in Phase 3
            (Core Academic) and Phase 4 (Academic Operations) — that data
            doesn&apos;t exist yet, so nothing is shown here.
          </CardDescription>
        </CardHeader>
      </Card>
    </div>
  );
}
