# Phase 11 — Public Web Redesign (Landing + Live Auction viewer)

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions and
[PHASE6.md](PHASE6.md) for the original web viewer.

**Last updated:** 2026-10-02
**Status:** implemented (backend + mobile + web-viewer), verified against the design in a browser. Deployed to Railway production 2026-10-02 (commit `ba31d66`, via `railway up` of a clean `git archive` -- not yet pushed to GitHub, so the next GitHub-triggered deploy must include these commits). Mobile scheduled-time picker not verified on-device (phone not connected).

---

## 1. Overview

A Claude Design redesign of the public web (`web-viewer/`) — handoff at
`C:\Users\rulfo\Downloads\Crichere Instructions\design_handoff_crichere_web` (README.md is the
spec; `design/*.dc.html` are high-fidelity references, not code to copy). Design project:
`https://claude.ai/design/p/8cfb9b49-de1e-4b6e-a069-8182cf150e76`.

| Route | Design file | Replaces |
|---|---|---|
| `/` | `Crichere Landing.dc.html` | `app/page.tsx` (was a one-paragraph placeholder) |
| `/leagues/[id]` | `Live Auction.dc.html` (8 views) | `components/LiveAuction.tsx` + module CSS |
| `/leagues/[id]` 404 | `Live Auction.dc.html` → `notFound` | `app/leagues/[id]/not-found.tsx` |
| shared | `Store Badge.dc.html` | new `components/StoreBadge.tsx` |

The Phase 6 "single dark theme" decision is superseded: landing uses the light brand palette
with dark hero/how-it-works sections; the auction page stays dark (broadcast look). Fixed per
section, still not OS-adaptive.

---

## 2. Compatibility check (2026-10-02)

**Stack:** compatible, no blockers. Next 16 App Router + CSS Modules + `next/font/google`
(Archivo, Instrument Sans, JetBrains Mono — self-hosted) cover it. All motion is Web Animations
API + CSS `linear()` easing with cubic fallbacks — **no new runtime dependency** (no Framer Motion).
Container units (`cqi`) are baseline since 2023.

**Data the backend already returns but `web-viewer/lib/api.ts` never typed** (type sync only):

| Design element | Existing backend field |
|---|---|
| Player photo | `AuctionStateResponse.currentPlayerPhotoUrl` |
| Role chip | `AuctionStateResponse.currentPlayerRole` |
| Pool progress | `playersTotal`, `playersSold` |
| SOLD / UNSOLD band | `lastResult { playerName, sold, franchiseName, amount }` |
| Ground in meta row | `LeagueResponse.groundName` |
| Franchise logos | `LeagueResponse.franchises[].logoUrl` |
| Completed-list photos | `PlayerAuctionResultResponse.photoUrl` |

`lastResult` is an explicit sold/unsold signal, so it replaces the handoff README's
"diff `results.playersWon` on player change" heuristic. The diff stays only as a fallback for a
missed intermediate SSE state.

**Data missing everywhere** — resolved by decision D1 below: batting/bowling style on the auction
state, role on results rows, a lot number, scheduled auction time, a "which league is live now" lookup.

---

## 3. Decisions Made

- **D1 — Full backend parity for design fields.** Backend adds:
  - `currentPlayerBattingStyle` / `currentPlayerBowlingStyle` on `AuctionStateResponse` (from the profile, public like name/role).
  - `playingRole` on `PlayerAuctionResultResponse`.
  - `currentLotNumber` (see D2) and `auctionScheduledAt` (see D3).
- **D2 — Lot number = persisted counter, shown as "Lot N" only.** Unsold players re-enter the
  random pool (`AuctionService.nextPlayer`), so "Lot N of M" could read "Lot 34 of 30". New
  `leagues.auction_lot_counter` (migration V18), +1 on every `nextPlayer`. Copy: `Lot 14 · 32 left
  in pool` (pool = still-`PENDING` count). Deviation from design copy "Lot N of M" — intentional.
- **D3 — Scheduled auction time end to end.** `leagues.auction_scheduled_at` (nullable
  `timestamptz`, V18), settable from the mobile Auction settings screen (date + time picker),
  exposed on `LeagueResponse`. Web "Not started" view shows it when set, hides it otherwise.
- **D4 — Landing stats ship as design placeholders** (1,240 leagues / 38,600 players / 9,64,000
  bids, hero card "Bids placed tonight 412"). Owner's call. **Must be replaced with real numbers
  before public launch** — tracked in DESIGN-REVIEW follow-ups.
- **D5 — "Live now" endpoint.** `GET /api/v1/auctions/live-now`, public: the `IN_PROGRESS`
  league with the most recent bid (fallback: most recently started). `204` when none — landing
  then hides every "Watch live" / "Watch a live auction" / "See one running" link. Short server
  cache (~15s); exposes nothing beyond what each league's public page already shows.
- **D6 — Icons as inline SVG components, QR as a static committed SVG.** The 33 Material Symbols
  Rounded SVGs become small React components (no icon-font download/flash, no external request).
  QR generated once by a script for the Play Store URL; regenerate if the URL changes. No
  third-party QR API (would leak traffic).
- **D8 — App not in any store yet (2026-10-02).** Store URLs come from `NEXT_PUBLIC_PLAY_STORE_URL`
  / `NEXT_PUBLIC_APP_STORE_URL`. Unset → that badge renders in the design's "Coming soon" variant
  (dashed border, not a link, gold "SOON" pill) — today, both. The QR card and the sticky mobile
  "Open" app banner render only once the Play URL is set. No QR library is added until then
  (adding one needs owner approval).
- **D7 — Dependency bumps.** Patch/minor to latest stable as step 0 (Next 16.3.8, React 19.3.0,
  eslint-config-next 16.3.8, vitest/vite/jsdom patches). TypeScript 7 and ESLint 10 are major
  jumps — separate phase.

---

## 4. Security notes

- No new authenticated surface. D5 is public read-only, returns only a league id already
  reachable via its public page; cached to bound DB load from anonymous traffic.
- D1 fields (batting/bowling style, role) are profile attributes already shown on the public
  roster/auction surfaces — same exposure posture as name/photo.
- External store links: `rel="noopener noreferrer"`, URLs from env vars, never user input.
- Images (`logoUrl`, `photoUrl`) render via plain `<img>` — no `next/image` `remotePatterns` widening.
- No CSP headers exist on the web viewer today — out of scope here, noted for the deploy phase.

---

## 5. Plan

0. Dependency bumps (D7); fetch `support.js` via design MCP (after `/design-login`) so the
   `.dc.html` references render for Playwright CSS extraction.
1. **Backend:** V18 migration (lot counter, scheduled-at), D1 DTO fields, D5 endpoint + tests.
2. **Mobile:** scheduled-at picker on Auction settings; DTO sync.
3. **Web — tokens & fonts:** light/dark palettes, three fonts, favicon/logo, icon components.
4. **Web — `StoreBadge`:** play / appstore / appstore coming-soon / qr, magnetic hover; store URLs from env.
5. **Web — Landing:** six sections, scroll reveals, parallax, hero phone loop, how-it-works scroll sequence, count-ups.
6. **Web — Live Auction:** `lib/api.ts` sync, all 8 states (loading, not started, live, sold, unsold, completed, reconnecting, not found), sticky mobile app banner, "Get the app" popover.
7. **Web — not-found.**
8. **Motion pass** against the README motion table; JS reduced-motion branch.
9. **Tests:** Vitest (lastResult SOLD/UNSOLD, reconnect, skeleton, below-min), e2e (landing links, every viewer state, live-now 204), mock-server fixtures extended. On-device check (CPH2487) + DESIGN-REVIEW follow-ups.

---

## 6. Implementation notes

### Backend (step 1)
- `V18__auction_lot_and_schedule.sql`: `leagues.auction_lot_counter INT NOT NULL DEFAULT 0`,
  `leagues.auction_scheduled_at TIMESTAMPTZ`.
- `AuctionService.nextPlayer()` increments the counter only when it actually opens a player (not on
  auto-complete); `undo` never touches it, so undoing a sale reopens the *same* lot.
- `AuctionStateResponse` + `currentPlayerBattingStyle`, `currentPlayerBowlingStyle`,
  `currentLotNumber` (null before the first lot), `playersPending` (the pool `next-player` draws
  from). `PlayerAuctionResultResponse` + `playingRole`. `LeagueResponse` + `auctionScheduledAt`.
- `AuctionSettingsSaveRequest.scheduledAt` optional; `null` clears it; no past-date check.
- `GET /api/v1/auctions/live-now` (`LiveNowController`/`LiveNowService`): `{leagueId, leagueName}`
  or `204`. `LeagueRepository.findLiveNow()` orders `IN_PROGRESS` leagues by latest unreversed bid
  (`NULLS LAST`), then `updated_at`. Memoized 15s (`AtomicReference`, null answers included).
  `permitAll()` GET; no CORS entry (fetched server-side by Next).

### Mobile (step 2)
- `LeagueDto.auctionScheduledAt` and `AuctionSettingsSaveRequestDto.scheduledAt` (ISO-8601 instant
  strings). `AuctionSettingsViewModel` pre-fills `scheduledAt`, `onScheduledAtChanged(String?)`
  sets/clears it, `submit()` sends it; it is never validated and never blocks Save.
- Android `AuctionSettingsScreen`: "Auction date & time (optional)" tap field after Bid increment
  -> Material3 date dialog ("Next") -> time dialog (default 7:00 PM) -> stored in the phone's zone
  as an instant; a 44dp clear button appears once set. iOS: a `DatePicker` section (authored only).
- The new auction-state fields need no mobile change (`ignoreUnknownKeys = true`).

### Web (steps 3-8)
- **Design matching**: the `.dc.html` sources carry exact inline styles, so CSS was copied from them
  rule for rule, then checked by measuring element boxes in the rendered design vs the app with
  Playwright (1440 / 768 / 360). Landing matches to the pixel at 1440; the auction page's boxes match
  wherever the data matches.
  - The design uses the browser's **content-box** default, so the old global `* { box-sizing:
    border-box }` was removed; elements the design marks border-box keep it.
  - JetBrains Mono has **no ₹ glyph**, so the design's rupee sign comes from the `monospace`
    fallback. `next/font`'s default size-adjusted Arial fallback drew a wider ₹, so the mono font
    uses `adjustFontFallback: false, fallback: ["monospace"]`.
- **Structure**: `app/page.tsx` (server, ISR 15s for live-now) + `components/landing/*`
  (`HeroPhone` demo loop, `LandingMotion` driving reveals / count-ups / parallax / how-it-works from
  `data-*` attributes); `components/LiveAuction.tsx` + `useAuctionStream.ts`; shared
  `components/ui/*` (`Icon`, `Logo`, `StoreBadge`, `GetAppPopover`); `lib/motion.ts`, `lib/store.ts`.
- **Breakpoints** follow the README (nav links + QR collapse under 820px, floating hero cards hide
  under 1180px, app banner under 720px) as CSS container queries. The design prototype itself never
  collapses them (its ResizeObserver state doesn't apply), so it disagrees below 820px -- the README wins.
- **SOLD / UNSOLD**: `lastResult` first; `detectOutcome()` (results diff) only when the in-between
  state was missed. A band holds 3s on the closed lot, then the newest state shows. The state
  already on screen at page open never replays a band.
- **Reconnecting**: EventSource `error` -> banner + attempt count + "last update Ns ago", live
  content dimmed to 50%. If the browser gives up (CLOSED), a new EventSource opens with 2/4/8/15s backoff.
- **Between lots** (no player open, no band): the card reads "Between lots / Next player coming up"
  -- not in the design (it always has a player up).
- **Copy deviations**: "Lot N · X left in pool" instead of "Lot N of M" (D2); role labels come from
  the 4-value `PlayingRole` enum ("All-rounder", not "Batting all-rounder"); franchise badges use
  derived initials ("SPA", "VC") since no short code exists; the Play badge also gets a "Coming
  soon" variant (the design only has one for the App Store) because neither app is published (D8).
- **Tests**: 32 Vitest (every viewer state, SOLD/UNSOLD + fallback, reconnect + backoff, skeleton,
  below-min, style chips, helpers, badges) and 11 Playwright e2e against the mock backend, whose
  fixtures now mirror the design's sample league with scripted SOLD / UNSOLD / dropped-connection streams.

### Deployment (2026-10-02)
- Web: https://web-viewer-production-dca0.up.railway.app -- backend:
  https://backend-production-f74e7.up.railway.app (project `crichere`, environment `production`).
- Backend `WEB_VIEWER_ORIGINS` was unset in production, which left CORS off and would have blocked
  the browser's SSE stream and results fetch; set to the web-viewer domain.
- Verified after deploy: landing 200, unknown league 404, live-now 204 (nothing live), CORS header
  on results + stream, and a real league page rendering from the stream.

## 7. Open items needing owner input

- Official Google Play / App Store badge artwork (design uses mock glyphs).
- Final Play Store URL -> set `NEXT_PUBLIC_PLAY_STORE_URL`; then generate the QR (needs a QR
  dependency -- ask first) and pass it to `<StoreBadge kind="qr" qrSrc=...>`.
- Whether the app gets an App Link for `/leagues/{id}` (decides what the mobile banner's "Open"
  does; today: the Play Store URL).
- Replace the placeholder landing stats (D4) before public launch.
- Footer About / Privacy / Terms link to `#` (no pages exist) and Contact is `hello@crichere.app`
  as designed -- confirm the address and add the pages.
