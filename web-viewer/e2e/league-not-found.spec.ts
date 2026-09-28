import { expect, test } from "@playwright/test";

test("an unknown league id shows the not-found page, not an error page", async ({ page }) => {
  await page.goto("/leagues/does-not-exist");
  await expect(page.getByRole("heading", { name: "League not found" })).toBeVisible();
});
