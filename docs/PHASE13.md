# Phase 13 — Branded link sharing on crichere.com (share cards + Android App Links)

Part of the Crichere full rewrite. Builds on the custom domain set up 2026-10-03
([PHASE11.md](PHASE11.md) "Custom domain"), the Phase 3 deep link ([PHASE3.md](PHASE3.md)) and the
Phase 6 public web viewer ([PHASE6.md](PHASE6.md)).

**Last updated:** 2026-10-03
**Status:** web + Android **implemented and tested locally (unit + e2e + production build); not yet
deployed; App Link verification and the share sheet not yet verified on-device.**

---

## 1. What this adds

Sharing a league used to send `crichere://leagues/{id}`: not clickable in WhatsApp, no preview, and a
dead link without the app. Now Share sends the https watch link `https://crichere.com/leagues/{id}`:

- **Preview card.** Chat apps and social sites (WhatsApp, Facebook, Telegram, X, LinkedIn, Slack) fetch
  the page once, without running JS, and build a card from its Open Graph tags. Each league page now
  carries its own title, description, canonical URL and a generated 1200×630 image.
- **Opens the app when installed.** An Android App Link: `https://crichere.com/leagues/{id}` opens the
  league inside the app if installed (verified via `/.well-known/assetlinks.json`), the web viewer
  otherwise.

## 2. Decisions made

| Decision | Why |
|---|---|
| **Design comes only from Claude Design; code transcribes it** | Standing project rule (2026-10-03). Two Claude Design files: "Crichere OG Card" (default card, exported PNG) and "Crichere League Share Card" (per-league template with a written spec). Nothing visual was invented in code. |
| **Per-league generated card** (logo or monogram, name, city/state, LIVE / "Auction on …" / nothing) | Chosen over "always the default card" and "league banner as the image" (2026-10-03): every league gets a recognisable, uncropped, on-brand card. |
| **Default card everywhere else** (landing page, unknown league, any render failure) | A share must never show no image. `public/og/crichere-default.png` is the exported Claude Design PNG, used unchanged. |
| **Share sends only the https link** (custom scheme no longer shared) | No public users yet (2026-10-03), so no installed base depends on the old text. `crichere://` stays registered: notification taps still use it. |
| **Share text:** "Join {league} on Crichere · {city} · watch the player auction live" + link | Approved 2026-10-03. `EXTRA_SUBJECT`/`EXTRA_TITLE` = league name. |
| **Android App Links now, iOS Universal Links later** | Chosen 2026-10-03. Android is testable on the phone today. iOS needs an Apple Developer Team ID, the Associated Domains entitlement and an Xcode build, none of which exist yet. |
| **assetlinks.json lists the debug key only for now** | No release signing config or Play listing exists. The release / Play App Signing SHA-256 must be **added** (not swapped) before release, or installed release builds won't open links in-app. |
| **Card dates in India time** ("14 Nov 2026, 7:30 PM", Asia/Kolkata) | The image is rendered once on the server and seen by everyone, so it can't follow a viewer's zone the way the live page does. |
| **LIVE = the league `live-now` returns** | No per-league status on the public league response; `live-now` reports one live league. If two auctions run at once, only one card shows LIVE. Acceptable for MVP; revisit if concurrent auctions become common. |
| **Static font weights, not the design's variable TTFs** | Satori (behind `next/og`) can't select weights from a variable font. Same families (Archivo 800/900, Instrument Sans 500/600), static instances from Google Fonts (OFL), in `web-viewer/assets/fonts/`. |
| **Contact email unchanged** (`hello@crichere.app`) | Until a `crichere.com` mailbox is confirmed. Only the landing page's sample link text moved to `crichere.com`. |

## 3. Implementation

### Web (`web-viewer/`)
- `lib/seo.ts`: `SITE_URL` (env `NEXT_PUBLIC_SITE_URL`, default `https://crichere.com`), default image,
  `truncate`, `leagueShareDescription` (organizer's description up to 160 chars, else "Follow {name}'s
  live player auction in {city} -- no account needed.").
- `app/layout.tsx`: `metadataBase`, canonical, `openGraph` (siteName, type, default image),
  `twitter: summary_large_image`.
- `app/leagues/[id]/page.tsx`: per-league title/description/url/canonical. Next merges metadata
  shallowly, so `openGraph` here replaces the layout's, and siteName/type are repeated. No image set
  here: the colocated `opengraph-image.tsx` adds `og:image` with width/height (file-based metadata wins).
- `app/leagues/[id]/opengraph-image.tsx`: the Claude Design spec in satori flexbox. Logo fetched
  server-side (3s timeout, png/jpeg/webp only, ≤2 MB), else the monogram. The render is forced inside
  the try (ImageResponse is lazy) so any failure, including unknown league, serves the default PNG.
  `Cache-Control: public, max-age=300`.
- `lib/og-card.ts`: monogram, name size bands, IST date, location line, status selection.
- `public/.well-known/assetlinks.json`: `com.crichere.app` + debug key SHA-256.
- File paths in `readFile` are literal so Next's output tracing ships the fonts/SVGs/PNG.

### Android (`mobile/androidApp/`)
- `LeagueLinks.kt` `leagueIdFromLink(scheme, host, pathSegments)`: accepts `crichere://leagues/{id}` and
  `https://crichere.com/leagues/{id}`, rejects other hosts/schemes/paths. `MainActivity` uses it.
- `AndroidManifest.xml`: second VIEW intent filter, `autoVerify="true"`, https / `crichere.com` /
  `pathPrefix=/leagues/`. The `crichere://` filter is kept.
- `LeagueDetailScreen.kt`: Share sends the https link + text above. Copy watch link is unchanged
  (already https via `webViewerBaseUrl`).

## 4. Verification

Done (2026-10-03):
- Vitest 46/46 (new: `lib/og-card.test.ts`, `lib/seo.test.ts`).
- Playwright 21/21 (new `e2e/share-card.spec.ts`, crawler UA, mock backend): league tags (title,
  description, url, site_name, image with 1200×630, twitter card); generic description fallback; landing
  page default image; PNG 1200×630 <600 KB for logo / SVG logo (monogram) / long name (3-line clamp,
  scheduled) / live / unknown; unknown league serves the exact default PNG; assetlinks.json is JSON.
- Pixel diff of the generated "Thar Strikers" card against the design's state 2c HTML rendered in
  Chromium with the same fonts: identical geometry; ~1.5% of pixels differ, all anti-aliased glyph
  edges plus a few px of text-shaping drift on the short lines (satori vs Chromium text shaping).
- `next build` clean, no warnings; output trace includes fonts, SVGs and the default PNG.
- Android: `LeagueLinksTest` 4/4, existing unit tests pass, `assembleDebug` builds.

Still open:
- Deploy, then: `curl -A facebookexternalhit/1.1` / `-A WhatsApp/2` on a real league, Facebook Sharing
  Debugger, a real WhatsApp send to self. Previews are cached per URL, so do this before any public share.
- On-device (CPH2487): `adb shell pm get-app-links com.crichere.app` shows `crichere.com: verified`;
  tapping a shared link opens the league; share sheet text; `crichere://` notification tap still works.
- Release / Play App Signing SHA-256 into `assetlinks.json` before release.
- iOS Universal Links: `apple-app-site-association` (no extension, `application/json`) in
  `public/.well-known/`, `applinks:crichere.com` entitlement, link handling in `iosAppApp.swift`.
- `www.crichere.com` still points at an old Railway target: add it as a Railway domain + redirect.
