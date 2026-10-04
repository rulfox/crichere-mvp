import { expect, test } from "@playwright/test";

// Design update #5 B3: /privacy and /terms share one template; placeholder text, kept out of search.
for (const { path, title } of [
  { path: "/privacy", title: "Privacy Policy" },
  { path: "/terms", title: "Terms of Service" },
]) {
  test(`${path} renders the legal template, noindex`, async ({ page }) => {
    await page.goto(path);

    await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();
    await expect(page.getByText("Last updated 4 October 2026")).toBeVisible();
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
    await expect(page.getByRole("link", { name: "Back to home" })).toHaveAttribute("href", "/");
  });
}

test("contents: sticky sidebar at 1280, disclosure at 360", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/terms");
  const sidebar = page.getByRole("navigation", { name: "On this page" }).first();
  await expect(sidebar).toBeVisible();
  await sidebar.getByRole("link", { name: "3. Leagues and auctions" }).click();
  await expect(page).toHaveURL(/#leagues-and-auctions$/);
  await expect(sidebar.getByRole("link", { name: "3. Leagues and auctions" })).toHaveAttribute("aria-current", "location");

  await page.setViewportSize({ width: 360, height: 800 });
  await expect(sidebar).toBeHidden();
  await page.locator("summary", { hasText: "On this page" }).click();
  await expect(page.getByRole("navigation", { name: "On this page" }).last().getByRole("link", { name: "2. Accounts" })).toBeVisible();
});
