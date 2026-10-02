import { expect, test } from "@playwright/test";

test("landing page shows the hero, every section, and coming-soon store badges while the app is unpublished", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { level: 1 })).toHaveText(/Build\.\s*Auction\.\s*Compete\./);
  await expect(page.getByRole("heading", { name: "From the first sign-up to the last squad." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Four steps from player pool to locked squads." })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Your league is one download away." })).toBeVisible();

  // No store URL is configured in the e2e run (docs/PHASE11.md D8): badges render as coming soon, not links.
  await expect(page.getByRole("img", { name: "Google Play, coming soon" }).first()).toBeVisible();
  await expect(page.getByRole("img", { name: "App Store, coming soon" }).first()).toBeVisible();
  await expect(page.getByRole("link", { name: "Get it on Google Play" })).toHaveCount(0);
});

test("'Watch a live auction' opens the league the live-now lookup returns", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("link", { name: "Watch a live auction" }).click();

  await expect(page).toHaveURL(/\/leagues\/league-live$/);
  await expect(page.getByRole("heading", { name: "Live Auction League", level: 1 })).toBeVisible();
});

test("'Get the app' jumps to the download section", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("link", { name: "Get the app" }).click();

  await expect(page).toHaveURL(/#download$/);
});
