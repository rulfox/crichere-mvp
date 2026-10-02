import { expect, test } from "@playwright/test";

test("not-started league shows the waiting notice, the scheduled time and the auction facts", async ({ page }) => {
  await page.goto("/leagues/league-not-started");

  await expect(page.getByRole("heading", { name: /auction hasn.t started yet/ })).toBeVisible();
  await expect(page.getByText("NOT STARTED", { exact: true })).toBeVisible();
  await expect(page.getByText(/Bidding is scheduled for/)).toBeVisible();
  const facts = page.getByLabel("Auction facts");
  await expect(facts.getByText("₹20,000")).toBeVisible();
  await expect(facts.getByText("6 of 6 claimed", { exact: false })).toBeVisible();
});

test("live league shows the player under the hammer, the bid, the ticker and the standings", async ({ page }) => {
  await page.goto("/leagues/league-live");

  await expect(page.getByText("Live feed")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Aswin Sudarsanan" })).toBeVisible();
  await expect(page.getByText("Lot 12 · 18 left in pool")).toBeVisible();
  await expect(page.getByLabel("₹1,20,000")).toBeVisible();
  await expect(page.getByText("Right-hand bat")).toBeVisible();

  const ticker = page.getByLabel("Recent bids");
  await expect(ticker.getByText("Ashes Komalapuram")).toBeVisible();

  const standings = page.getByLabel("Franchise standings");
  await expect(standings.getByText("Mannancherry United")).toBeVisible();
  await expect(standings.getByText("LEADING")).toBeVisible();
});

test("a sold lot shows the SOLD band, then moves on", async ({ page }) => {
  await page.goto("/leagues/league-sold");
  await expect(page.getByRole("heading", { name: "Aswin Sudarsanan" })).toBeVisible();

  const band = page.getByRole("status");
  await expect(band).toContainText("SOLD");
  await expect(band).toContainText("to Victory CC");
  await expect(page.getByRole("heading", { name: "Next player coming up" })).toBeVisible({ timeout: 6000 });
});

test("an unsold lot shows the UNSOLD band", async ({ page }) => {
  await page.goto("/leagues/league-unsold");

  await expect(page.getByText("Sreeraj returns to the pool")).toBeVisible();
});

test("a dropped connection shows the reconnecting banner", async ({ page }) => {
  await page.goto("/leagues/league-flaky");
  await expect(page.getByRole("heading", { name: "Aswin Sudarsanan" })).toBeVisible();

  await expect(page.getByText("Connection lost. Reconnecting…")).toBeVisible({ timeout: 10000 });
  await expect(page.getByText("Reconnecting", { exact: true })).toBeVisible();
});

test("completed league shows the summary and final squads, flagging a squad below the minimum", async ({ page }) => {
  await page.goto("/leagues/league-completed");

  await expect(page.getByRole("heading", { name: "Auction complete" })).toBeVisible();
  await expect(page.getByText("COMPLETED", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Rising Stars" })).toBeVisible();
  await expect(page.getByText("Deepak Boche")).toBeVisible();
  await expect(page.getByText("Below squad minimum · 3 of 4")).toBeVisible();
});

test("the wordmark returns to the landing page", async ({ page }) => {
  await page.goto("/leagues/league-live");

  await page.getByRole("link", { name: "Crichere home" }).click();

  await expect(page).toHaveURL("/");
});
