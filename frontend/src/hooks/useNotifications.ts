import { useContext } from "react";

import { NotificationContext } from "@/context/NotificationContext";

/** Reads `NotificationContext`; throws if used outside `<NotificationProvider>` so misuse fails loudly. */
export function useNotifications() {
  const ctx = useContext(NotificationContext);
  if (!ctx) {
    throw new Error("useNotifications must be used within a NotificationProvider");
  }
  return ctx;
}
