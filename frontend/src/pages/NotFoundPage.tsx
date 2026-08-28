import { Link } from "react-router-dom";

import { buttonVariants } from "@/components/ui/button";

export default function NotFoundPage() {
  return (
    <div className="flex min-h-svh flex-col items-center justify-center gap-4 bg-background px-4 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">404</h1>
      <p className="text-muted-foreground">This page doesn&apos;t exist.</p>
      <Link to="/" className={buttonVariants({ variant: "default" })}>
        Go home
      </Link>
    </div>
  );
}
