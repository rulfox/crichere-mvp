import { expect, test } from "@playwright/test";

/*
 * Checks the production image (Caddy + generated CSP) in a real browser: no page breaks its own
 * Content-Security-Policy. Run against a container: HOST_CHECK_URL=http://localhost:18080 and
 * `npx playwright test e2e/host.spec.ts --project=desktop --grep @host`.
 */
const base = process.env.HOST_CHECK_URL;

test("every page loads under its CSP @host", async ({ page }) => {
  test.skip(!base, "HOST_CHECK_URL not set");
  const violations: string[] = [];
  page.on("console", (m) => {
    if (/Content Security Policy|Refused to/i.test(m.text())) violations.push(`${page.url()}: ${m.text()}`);
  });
  for (const path of ["/signin/", "/", "/reports/", "/reports/view/?id=x", "/leagues/", "/leagues/view/?id=x", "/users/", "/users/view/?id=x", "/suspended/", "/audit/", "/nope/"]) {
    const response = await page.goto(`${base}${path}`, { waitUntil: "domcontentloaded" });
    expect(response?.headers()["x-frame-options"]).toBe("DENY");
    await page.waitForTimeout(400);
  }
  await page.goto(`${base}/signin/`, { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await expect(page.getByText("Production · api.crichere.com")).toBeVisible();
  expect(violations).toEqual([]);
});
