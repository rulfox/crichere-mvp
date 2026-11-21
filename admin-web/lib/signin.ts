// Helpers for the sign-in page (kept out of the page file, which may only export the page).

/**
 * Only paths on this site, so a crafted link can't send the admin elsewhere after sign-in. Resolved
 * with the URL parser and compared by origin: string checks miss forms the parser normalises, such
 * as "/\t/evil.example.com" (tabs and newlines are stripped, leaving "//evil.example.com").
 */
export function safeNext(next: string | null, origin = typeof window === "undefined" ? "http://localhost" : window.location.origin): string {
  if (!next || /[\u0000-\u001F\u007F\\]/.test(next)) return "/";
  try {
    const url = new URL(next, origin);
    if (url.origin !== origin) return "/";
    return url.pathname + url.search + url.hash;
  } catch {
    return "/";
  }
}

/** The email inside a Google ID token, only to name the account in the "not allowed" message. */
export function tokenEmail(credential: string): string | null {
  try {
    const payload = credential.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return (JSON.parse(atob(payload)) as { email?: string }).email ?? null;
  } catch {
    return null;
  }
}
