import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import * as authService from "@/services/authService";
import { extractErrorMessage } from "@/utils/apiError";
import { isValidEmail } from "@/utils/validation";

export default function ForgotPasswordPage() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);

    const trimmed = email.trim();
    if (!trimmed) {
      setFieldError("Email is required.");
      return;
    }
    if (!isValidEmail(trimmed)) {
      setFieldError("Enter a valid email address.");
      return;
    }
    setFieldError(null);

    setIsSubmitting(true);
    try {
      // Always 200 and non-enumerating: this navigates on to the OTP step
      // whether or not the email is registered, so the form never reveals
      // which emails have accounts.
      await authService.forgotPassword({ email: trimmed });
      navigate("/verify-otp", { state: { email: trimmed } });
    } catch (err) {
      setFormError(extractErrorMessage(err));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Forgot password</CardTitle>
        <CardDescription>
          Enter your account email and we&apos;ll send a one-time code to reset your
          password.
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
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              aria-invalid={!!fieldError}
            />
            {fieldError && <p className="text-xs text-destructive">{fieldError}</p>}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Sending…" : "Send code"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          <Link to="/login" className="text-foreground hover:underline">
            Back to log in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
