import { expect, test, type APIRequestContext } from "@playwright/test";

/**
 * Link previews (docs/PHASE13.md): what a chat app's crawler sees when someone shares a league.
 * Fetched with a crawler User-Agent and no browser, because crawlers don't run JS and Next serves
 * them blocking (not streamed) metadata.
 */
const CRAWLER = { "User-Agent": "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)" };

async function metaTags(request: APIRequestContext, path: string) {
  const response = await request.get(path, { headers: CRAWLER });
  const html = await response.text();
  const tags = new Map<string, string>();
  for (const [, key, value] of html.matchAll(/<meta (?:property|name)="([^"]+)" content="([^"]*)"/g)) {
    if (!tags.has(key)) tags.set(key, value.replace(/&#x27;/g, "'").replace(/&amp;/g, "&"));
  }
  return { status: response.status(), tags };
}

test("a shared league carries its own title, description, url and generated card", async ({ request }) => {
  const { status, tags } = await metaTags(request, "/leagues/league-share-logo");

  expect(status).toBe(200);
  expect(tags.get("og:title")).toBe("Kochi Super Sixes");
  expect(tags.get("og:description")).toBe("Six teams, one auction night.");
  expect(tags.get("og:url")).toBe("http://localhost:3000/leagues/league-share-logo");
  expect(tags.get("og:site_name")).toBe("Crichere");
  expect(tags.get("og:image")).toMatch(/^http:\/\/localhost:3000\/leagues\/league-share-logo\/opengraph-image/);
  expect(tags.get("og:image:width")).toBe("1200");
  expect(tags.get("og:image:height")).toBe("630");
  expect(tags.get("twitter:card")).toBe("summary_large_image");
});

test("a league without a description gets the generic line", async ({ request }) => {
  const { tags } = await metaTags(request, "/leagues/league-share-svg-logo");
  expect(tags.get("og:description")).toBe("Follow Thar Strikers's live player auction in Jodhpur -- no account needed.");
});

test("the landing page uses the default Crichere card", async ({ request }) => {
  const { tags } = await metaTags(request, "/");
  expect(tags.get("og:image")).toBe("http://localhost:3000/og/crichere-default.png");
  expect(tags.get("og:url")).toBe("http://localhost:3000");
  expect(tags.get("twitter:card")).toBe("summary_large_image");
});

for (const id of ["league-share-logo", "league-share-svg-logo", "league-share-long", "league-live", "does-not-exist"]) {
  test(`the share card for ${id} is a 1200x630 PNG under 600 KB`, async ({ request }) => {
    const response = await request.get(`/leagues/${id}/opengraph-image`);
    expect(response.status()).toBe(200);
    expect(response.headers()["content-type"]).toBe("image/png");
    const png = await response.body();
    expect(png.length).toBeLessThan(600_000);
    // PNG IHDR: width and height are big-endian at bytes 16 and 20.
    expect(png.readUInt32BE(16)).toBe(1200);
    expect(png.readUInt32BE(20)).toBe(630);
  });
}

test("an unknown league's card falls back to the default Crichere card", async ({ request }) => {
  const [unknown, fallback] = await Promise.all([
    request.get("/leagues/does-not-exist/opengraph-image").then((r) => r.body()),
    request.get("/og/crichere-default.png").then((r) => r.body()),
  ]);
  expect(unknown.equals(fallback)).toBe(true);
});

test("Android App Links can verify the domain", async ({ request }) => {
  const response = await request.get("/.well-known/assetlinks.json");
  expect(response.status()).toBe(200);
  expect(response.headers()["content-type"]).toContain("application/json");
  const [statement] = await response.json();
  expect(statement.relation).toContain("delegate_permission/common.handle_all_urls");
  expect(statement.target.package_name).toBe("com.crichere.app");
  expect(statement.target.sha256_cert_fingerprints.length).toBeGreaterThan(0);
});
