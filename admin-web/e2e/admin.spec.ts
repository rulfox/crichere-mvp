import { ADMIN_EMAIL, expect, seedReport, signIn, sql, test } from "./fixtures";

const unique = () => Math.random().toString(36).slice(2, 7);

test.describe("signed out", () => {
  test("every page sends you to sign in, which names the backend and the alert email", async ({ page }) => {
    await page.goto("/reports/");
    await expect(page).toHaveURL(/\/signin\/\?next=%2Freports%2F/);
    await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
    await expect(page.getByText("Every sign-in sends an email")).toBeVisible();
    await expect(page.getByText("Local")).toBeVisible();
  });
});

test.describe("signed in", () => {
  test.beforeEach(async ({ context }) => {
    await signIn(context);
  });

  test("overview shows the KPIs, live section and health checks", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
    await expect(page.locator(".kpi").first()).toContainText("Users");
    await expect(page.getByRole("heading", { name: "Live now" })).toBeVisible();
    await expect(page.locator(".live")).toContainText(/Live, updated|Connecting/);
    await expect(page.getByRole("heading", { name: "Checks" })).toBeVisible();
    await page.getByRole("button", { name: "Table" }).first().click();
    await expect(page.getByRole("columnheader", { name: "New users" })).toBeVisible();
  });

  test("a report is dismissed with a note and lands in the audit log", async ({ page }) => {
    const name = `E2E Target ${unique()}`;
    const { reportId } = seedReport(name);
    await page.goto("/reports/");
    await page.getByRole("row", { name: new RegExp(name) }).click();
    await expect(page.getByRole("heading", { name: "Spam or misleading" })).toBeVisible();
    await expect(page.getByText("Keeps posting fake fees")).toBeVisible();

    const dismiss = page.getByRole("button", { name: "Dismiss" });
    await expect(dismiss).toBeDisabled();
    await page.getByLabel("Decision note").fill("Duplicate, handled in another report");
    await dismiss.click();
    await expect(page.getByRole("status")).toContainText("Report dismissed.");
    expect(sql(`select status from reports where id = '${reportId}'`)).toBe("DISMISSED");
    expect(sql(`select admin_email from moderation_actions where report_id = '${reportId}' and action = 'DISMISS_REPORT'`)).toBe(ADMIN_EMAIL);

    await page.goto("/audit/");
    await expect(page.getByRole("row", { name: /Dismissed.*Duplicate, handled/ }).first()).toBeVisible();
  });

  test("suspend needs a note, then shows in Suspended and can be undone", async ({ page }) => {
    const name = `E2E Suspend ${unique()}`;
    const { userId, reportId } = seedReport(name);
    await page.goto(`/reports/view/?id=${reportId}`, { waitUntil: "domcontentloaded" });
    await page.getByRole("button", { name: "Suspend", exact: true }).click();
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByRole("button", { name: "Suspend account" })).toBeDisabled();
    await dialog.getByLabel("Decision note").fill("Fake league collecting fees");
    await dialog.getByRole("button", { name: "Suspend account" }).click();
    await expect(page.getByRole("status")).toContainText("suspended");
    expect(sql(`select suspended_at is not null from users where id = '${userId}'`)).toBe("t");

    await page.goto("/suspended/");
    const row = page.getByRole("row", { name: new RegExp(name) });
    await expect(row).toContainText("Fake league collecting fees");
    await row.getByRole("button", { name: "Unsuspend" }).click();
    await page.getByRole("dialog").getByLabel("Decision note").fill("Appeal accepted");
    await page.getByRole("dialog").getByRole("button", { name: "Unsuspend" }).click();
    await expect(page.getByRole("status")).toContainText("can sign in again");
    expect(sql(`select suspended_at is null from users where id = '${userId}'`)).toBe("t");
  });

  test("search finds a person, and the internal switch is audited with undo", async ({ page }) => {
    const name = `E2E Person ${unique()}`;
    const { userId } = seedReport(name);
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible();
    await page.keyboard.press("/");
    await page.getByPlaceholder("League or person name, or an ID").fill(name);
    await page.getByRole("button", { name: new RegExp(name) }).click();
    await expect(page.getByRole("heading", { name })).toBeVisible();
    await expect(page.getByText("Never shown. Use Find by phone number.")).toBeVisible();

    await page.getByRole("switch", { name: "Internal account" }).click();
    await expect(page.getByRole("status")).toContainText("marked as internal");
    expect(sql(`select is_internal from users where id = '${userId}'`)).toBe("t");
    await page.getByRole("status").getByRole("button", { name: "Undo" }).click();
    await expect.poll(() => sql(`select is_internal from users where id = '${userId}'`)).toBe("f");
    expect(sql(`select count(*) from moderation_actions where user_id = '${userId}' and action in ('MARK_INTERNAL', 'UNMARK_INTERNAL')`)).toBe("2");
  });

  test("lists for leagues and users load with filters", async ({ page }) => {
    // Local test profiles point photos at the Android emulator host (10.0.2.2), which never answers
    // from a desktop browser, so these pages are awaited to DOM ready rather than full load.
    await page.goto("/leagues/", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: "Leagues" })).toBeVisible();
    await expect(page.locator(".tablewrap")).toBeVisible();
    await page.goto("/users/", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: "Users" })).toBeVisible();
    await page.getByRole("button", { name: "Find by phone number" }).click();
    await page.getByRole("textbox", { name: "Phone number" }).fill("+91 90000 00000");
    await page.getByRole("button", { name: "Find", exact: true }).click();
    await expect(page.getByText("No account matches")).toBeVisible();
    expect(sql(`select note from moderation_actions where action = 'PHONE_LOOKUP' order by created_at desc limit 1`)).toBe("0 matches");
  });

  test("the theme can be switched and is remembered", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: /e2e-admin/ }).click();
    await page.getByRole("radio", { name: "Dark" }).click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await page.reload();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  });

  test("a revoked session shows Your session ended", async ({ page, context }) => {
    await page.goto("/audit/");
    await expect(page.getByRole("heading", { name: "Audit log" })).toBeVisible();
    sql(`update admin_sessions set revoked_at = now() where email = '${ADMIN_EMAIL}' and revoked_at is null`);
    await page.getByLabel("Action").selectOption("SUSPEND");
    await expect(page.getByRole("dialog", { name: "Your session ended" })).toBeVisible();
    await page.getByRole("button", { name: "Sign in with Google" }).click();
    await expect(page).toHaveURL(/\/signin\/\?next=%2Faudit%2F/);
    void context;
  });
});

test("phone layout uses bottom tabs @phone", async ({ page, context }) => {
  await signIn(context);
  await page.goto("/");
  await expect(page.locator(".ptabs")).toBeVisible();
  await expect(page.locator(".side")).toBeHidden();
});
