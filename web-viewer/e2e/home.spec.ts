import { expect, test } from "@playwright/test";

test("home page explains this app has no league browser of its own", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Crichere Watch" })).toBeVisible();
  await expect(page.getByText(/open the watch link an organizer shared/i)).toBeVisible();
});
