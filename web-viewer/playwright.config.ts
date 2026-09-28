import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end suite for the whole app -- runs against a real `next dev` server and a small mock
 * backend (`e2e/mock-server.mjs`, fixtures in `e2e/fixtures.mjs`), never the real Spring Boot
 * backend. That keeps the suite fast and deterministic instead of depending on live auction
 * state, and means it never needs Postgres/Docker running to pass.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: [
    {
      command: "node e2e/mock-server.mjs",
      port: 4310,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: "npx next dev",
      port: 3000,
      reuseExistingServer: !process.env.CI,
      env: { NEXT_PUBLIC_API_BASE_URL: "http://localhost:4310" },
    },
  ],
});
