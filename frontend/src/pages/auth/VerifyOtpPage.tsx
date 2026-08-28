import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import * as authService from "@/services/authService";
import { extractErrorMessage } from "@/utils/apiError";
import { OTP_LENGTH, isValidOtp } from "@/utils/validation";

interface NavState {
  email?: string;
}

export default function VerifyOtpPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const email = (location.state as NavState | null)?.email;

  const [otp, setOtp] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!email) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Verify code</CardTitle>
          <CardDescription>
            We couldn&apos;t find which account you&apos;re resetting. Start over from
            the forgot password page.
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

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);

    if (!isValidOtp(otp)) {
      setFieldError(`Enter the ${OTP_LENGTH}-digit code sent to your email.`);
      return;
    }
    setFieldError(null);

    setIsSubmitting(true);
    try {
      // Read-only check — the backend does not consume the OTP or issue a separate
      // reset credential here, so the email/otp pair itself is carried forward and
      // resent (and re-validated) on the actual reset call.
      const trimmedOtp = otp.trim();
      await authService.verifyOtp({ email: email as string, otp: trimmedOtp });
      navigate("/reset-password", { state: { email, otp: trimmedOtp } });
    } catch (err) {
      setFormError(extractErrorMessage(err, "That code is invalid or has expired."));
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Verify code</CardTitle>
        <CardDescription>
          Enter the {OTP_LENGTH}-digit code sent to {email}.
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
            <Label htmlFor="otp">Verification code</Label>
            <Input
              id="otp"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={OTP_LENGTH}
              value={otp}
              onChange={(event) => setOtp(event.target.value.replace(/\D/g, ""))}
              aria-invalid={!!fieldError}
            />
            {fieldError && <p className="text-xs text-destructive">{fieldError}</p>}
          </div>

          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? "Verifying…" : "Verify code"}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-muted-foreground">
          <Link to="/forgot-password" className="text-foreground hover:underline">
            Resend code
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
