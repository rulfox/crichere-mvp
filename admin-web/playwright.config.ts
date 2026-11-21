import { defineConfig, devices } from "@playwright/test";

/*
 * End-to-end against a real local backend (docs/PHASE20.md Step 10). Before running:
 *   1. backend: `docker compose up -d`, then
 *      ADMIN_EMAILS=e2e-admin@example.com ./gradlew bootRun --args="--spring.profiles.active=local"
 *   2. here: `NEXT_PUBLIC_API_BASE=http://localhost:8080 npm run build`, then `npm run test:e2e`.
 * Google sign-in itself can't run unattended, so e2e/fixtures.ts seeds an admin session row for the
 * allowlisted e2e email and sets its cookie: the backend checks it exactly as it checks a real one.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: { baseURL: "http://localhost:3001", trace: "retain-on-failure" },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 1000 } }, grepInvert: /@phone|@shots|@host/ },
    { name: "phone", use: { ...devices["Pixel 7"] }, grep: /@phone/ },
    { name: "host", use: { ...devices["Desktop Chrome"] }, grep: /@host/ },
  ],
  webServer: { command: "node e2e/serve.mjs", url: "http://localhost:3001/signin/", reuseExistingServer: true },
});
