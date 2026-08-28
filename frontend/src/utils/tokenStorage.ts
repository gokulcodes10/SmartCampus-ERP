/**
 * Persists the JWT across reloads. Isolated behind this module so the rest of
 * the app (AuthContext, the axios interceptor) never touches `localStorage`
 * directly — if the storage strategy ever changes, this is the only file
 * that moves.
 */

const TOKEN_KEY = "smartcampus_erp_token";

export function getStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    // localStorage can throw in private-browsing / storage-disabled contexts.
    // Treat that the same as "no token stored" rather than crashing the app.
    return null;
  }
}

export function setStoredToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // See getStoredToken — auth still works for the current tab session,
    // it just won't survive a reload.
  }
}

export function clearStoredToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // See getStoredToken.
  }
}
