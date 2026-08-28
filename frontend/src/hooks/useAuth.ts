import { useContext } from "react";

import { AuthContext } from "@/context/AuthContext";

/** Reads `AuthContext`; throws if used outside `<AuthProvider>` so misuse fails loudly. */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
