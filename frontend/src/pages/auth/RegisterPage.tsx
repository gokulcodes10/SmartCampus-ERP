import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { extractErrorMessage } from "@/utils/apiError";
import { useAuth } from "@/hooks/useAuth";
import { MIN_PASSWORD_LENGTH, isValidEmail, isValidPassword } from "@/utils/validation";

interface FieldErrors {
  fullName?: string;
  email?: string;
  password?: string;
  confirmPassword?: string;
}

export default function RegisterPage() {
  const { registerStudent } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function validate(): boolean {
    const errors: FieldErrors = {};
    if (!fullName.trim()) errors.fullName = "Full name is required.";
    if (!email.trim()) errors.email = "Email is required.";
    else if (!isValidEmail(email)) errors.email = "Enter a valid email address.";
    if (!password) errors.password = "Password is required.";
    else if (!isValidPassword(password))
      errors.password = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
    if (confirmPassword !== password) errors.confirmPassword = "Passwords do not match.";
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!validate()) return;

    setIsSubmitting(true);
    try {
      await registerStudent(email.trim(), password, fullName.trim());
      navigate("/login", { replace: true, state: { registered: true } });
    } catch (err) {
      setFormError(extractErrorMessage(err, "Registration failed. Please try again."));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Create a student account</CardTitle>
        <CardDescription>
          Self-registration is for students only. Faculty and admin accounts are
          provisioned by an administrator.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={handleSubmit} noValidate>
          {formError && (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="fullName">Full name</Label>
            <Input
              id="fullName"
              autoComplete="name"
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              aria-invalid={!!fieldErrors.fullName}
            />
            {fieldErrors.fullName && (
              <p className="text-xs text-destructive">{fieldErrors.fullName}</p>
            )}
          </div>

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
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={!!fieldErrors.password}
            />
            {fieldErrors.password && (
              <p className="text-xs text-destructive">{fieldErrors.password}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="confirmPassword">Confirm password</Label>
            <Input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              aria-invalid={!!fieldErrors.confirmPassword}
            />
            {fieldErrors.confirmPassword && (
              <p className="text-xs text-destructive">{fieldErrors.confirmPassword}</p>
            )}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Creating account…" : "Create account"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link to="/login" className="text-foreground hover:underline">
            Log in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
