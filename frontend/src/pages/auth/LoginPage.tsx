import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { dashboardPathForRole } from "@/routes/dashboardPath";
import { extractErrorMessage } from "@/utils/apiError";
import { useAuth } from "@/hooks/useAuth";
import { isValidEmail } from "@/utils/validation";

interface FieldErrors {
  email?: string;
  password?: string;
}

interface NavState {
  from?: { pathname: string };
  registered?: boolean;
  passwordReset?: boolean;
}

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const navState = (location.state as NavState | null) ?? null;

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function validate(): boolean {
    const errors: FieldErrors = {};
    if (!email.trim()) errors.email = "Email is required.";
    else if (!isValidEmail(email)) errors.email = "Enter a valid email address.";
    if (!password) errors.password = "Password is required.";
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    setIsSubmitting(true);
    try {
      const user = await login(email.trim(), password);
      const redirectTo = navState?.from?.pathname ?? dashboardPathForRole(user.role);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setFormError(extractErrorMessage(err, "Invalid email or password."));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Log in</CardTitle>
        <CardDescription>Enter your SmartCampus ERP credentials.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={handleSubmit} noValidate>
          {navState?.registered && (
            <Alert>
              <AlertDescription>Account created. You can log in now.</AlertDescription>
            </Alert>
          )}
          {navState?.passwordReset && (
            <Alert>
              <AlertDescription>Password reset. Log in with your new password.</AlertDescription>
            </Alert>
          )}
          {formError && (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              aria-invalid={!!fieldErrors.email}
            />
            {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <Label htmlFor="password">Password</Label>
              <Link
                to="/forgot-password"
                className="text-xs text-muted-foreground hover:text-foreground hover:underline"
              >
                Forgot password?
              </Link>
            </div>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={!!fieldErrors.password}
            />
            {fieldErrors.password && (
              <p className="text-xs text-destructive">{fieldErrors.password}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Logging in…" : "Log in"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Don&apos;t have an account?{" "}
          <Link to="/register" className="text-foreground hover:underline">
            Register
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
