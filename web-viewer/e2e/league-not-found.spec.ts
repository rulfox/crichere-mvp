import { expect, test } from "@playwright/test";

test("an unknown league id shows the not-found page, not an error page", async ({ page }) => {
  const response = await page.goto("/leagues/does-not-exist");

  expect(response?.status()).toBe(404);
  await expect(page.getByRole("heading", { name: "We couldn’t find that league" })).toBeVisible();
  await page.getByRole("link", { name: "Go to Crichere" }).click();
  await expect(page).toHaveURL("/");
});
