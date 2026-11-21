// Helpers for the sign-in page (kept out of the page file, which may only export the page).

/** Only same-site paths, so a crafted link can't send the admin elsewhere after sign-in. */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.includes("\\")) return "/";
  return next;
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
