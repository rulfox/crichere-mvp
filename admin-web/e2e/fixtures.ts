import { test as base, type BrowserContext } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { createHash, randomBytes, randomUUID } from "node:crypto";

/*
 * Test data straight into the local database (docker container backend-postgres-1), the same way the
 * backend's own integration tests seed rows. Only the e2e email below is on the local allowlist.
 */
export const ADMIN_EMAIL = "e2e-admin@example.com";
export const API = "http://localhost:8080";
const CONTAINER = process.env.E2E_PG_CONTAINER ?? "backend-postgres-1";

export function sql(statement: string): string {
  return execFileSync("docker", ["exec", "-i", CONTAINER, "psql", "-U", "crichere", "-d", "crichere_dev", "-At", "-v", "ON_ERROR_STOP=1"], { input: statement, encoding: "utf8" }).trim();
}

const q = (s: string) => `'${s.replace(/'/g, "''")}'`;

/** A live admin session row, as AdminSessionService would write after a Google sign-in. */
export function seedSession(): { raw: string; id: string } {
  const raw = randomBytes(32).toString("base64url");
  const id = randomUUID();
  const hash = createHash("sha256").update(raw).digest("hex");
  sql(`insert into admin_sessions (id, token_hash, email, expires_at, user_agent) values (${q(id)}, ${q(hash)}, ${q(ADMIN_EMAIL)}, now() + interval '30 minutes', 'playwright');`);
  return { raw, id };
}

export async function signIn(context: BrowserContext): Promise<string> {
  const { raw, id } = seedSession();
  await context.addCookies([{ name: "crichere_admin", value: raw, url: `${API}/api/v1/admin`, httpOnly: true, sameSite: "Strict" }]);
  return id;
}

/** A person with a profile and an open report about them. */
export function seedReport(name: string): { userId: string; reportId: string } {
  const userId = randomUUID();
  const reportId = randomUUID();
  sql(`
    insert into users (id, phone_lookup_hash, phone_encrypted, created_at) values (${q(userId)}, ${q(`e2e-${userId}`)}, 'e2e', now() - interval '3 days');
    insert into profiles (user_id, name, state, district) values (${q(userId)}, ${q(name)}, 'Kerala', 'Thrissur');
    insert into reports (id, target_type, target_user_id, reason, details, snapshot, created_at)
      values (${q(reportId)}, 'USER', ${q(userId)}, 'SPAM_OR_MISLEADING', 'Keeps posting fake fees', ${q(JSON.stringify({ name, photoUrl: null }))}, now() - interval '20 hours');
  `);
  return { userId, reportId };
}

export const test = base;
export { expect } from "@playwright/test";
