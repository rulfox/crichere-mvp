import { seedReport, signIn, test } from "./fixtures";

const dir = process.env.SHOTS_DIR ?? "test-results/shots";

test("screenshots for design review @shots", async ({ page, context }) => {
  await page.goto("/signin/", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1600);
  await page.screenshot({ path: `${dir}/signin.png` });
  await signIn(context);
  const { reportId } = seedReport("Nikhil Das");
  await page.goto("/", { waitUntil: "domcontentloaded" });
  await page.getByRole("heading", { name: "Checks" }).waitFor();
  await page.waitForTimeout(800);
  await page.screenshot({ path: `${dir}/overview.png`, fullPage: true });
  await page.goto(`/reports/view/?id=${reportId}`, { waitUntil: "domcontentloaded" });
  await page.getByText("Evidence").waitFor();
  await page.screenshot({ path: `${dir}/report.png`, fullPage: true });
  await page.goto("/reports/", { waitUntil: "domcontentloaded" });
  await page.getByRole("table").waitFor();
  await page.screenshot({ path: `${dir}/queue.png` });
});
