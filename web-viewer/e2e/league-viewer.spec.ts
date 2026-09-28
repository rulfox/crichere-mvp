import { expect, test } from "@playwright/test";

test("not-started league shows the waiting state with league facts", async ({ page }) => {
  await page.goto("/leagues/league-not-started");
  await expect(page.getByRole("heading", { name: /auction hasn.t started yet/ })).toBeVisible();
  await expect(page.getByText("Starts 2026-10-04")).toBeVisible();
  await expect(page.getByText(/Base price/)).toBeVisible();
});

test("live league shows the on-the-block player, bid ticker, and standings", async ({ page }) => {
  await page.goto("/leagues/league-live");

  await expect(page.locator("header").getByText("Live", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Rahul Sharma" })).toBeVisible();
  await expect(page.getByText("Thunder Kings leading")).toBeVisible();

  const ticker = page.getByLabel("Recent bids");
  await expect(ticker.getByText("Coastal Strikers")).toBeVisible();

  const standings = page.getByLabel("Squads so far");
  await expect(standings.getByText("Thunder Kings")).toBeVisible();
  await expect(standings.getByText("Below minimum squad size").first()).toBeVisible();
});

test("completed league shows final results per franchise", async ({ page }) => {
  await page.goto("/leagues/league-completed");

  await expect(page.getByRole("heading", { name: "Final results" })).toBeVisible();
  await expect(page.getByText("Rahul Sharma")).toBeVisible();
  await expect(page.getByText("Vikram Rao")).toBeVisible();
  await expect(page.getByText(/Spent/)).toBeVisible();
});
