import { useState } from "react";
import type { FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import * as authService from "@/services/authService";
import { extractErrorMessage } from "@/utils/apiError";
import { MIN_PASSWORD_LENGTH, isValidPassword } from "@/utils/validation";

interface NavState {
  email?: string;
  otp?: string;
}

interface FieldErrors {
  password?: string;
  confirmPassword?: string;
}

export default function ResetPasswordPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const navState = location.state as NavState | null;
  const email = navState?.email;
  const otp = navState?.otp;

  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!email || !otp) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Reset password</CardTitle>
          <CardDescription>
            Your verification session has expired or was never started. Start over
            from the forgot password page.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button className="w-full" onClick={() => navigate("/forgot-password")}>
            Back to forgot password
          </Button>
        </CardContent>
      </Card>
    );
  }

  function validate(): boolean {
    const errors: FieldErrors = {};
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
      await authService.resetPassword({
        email: email as string,
        otp: otp as string,
        newPassword: password,
      });
      navigate("/login", { replace: true, state: { passwordReset: true } });
    } catch (err) {
      setFormError(
        extractErrorMessage(err, "Could not reset your password. The link may have expired."),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Reset password</CardTitle>
        <CardDescription>Choose a new password for your account.</CardDescription>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={handleSubmit} noValidate>
          {formError && (
            <Alert variant="destructive">
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="password">New password</Label>
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
            <Label htmlFor="confirmPassword">Confirm new password</Label>
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
            {isSubmitting ? "Resetting…" : "Reset password"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
