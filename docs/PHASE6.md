# Phase 6 — Public Web Viewer

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions, and
[PHASE5.md](PHASE5.md) for the live auction engine this phase watches.

**Last updated:** 2026-09-13
**Status:** implemented (backend + web-viewer, manually verified end-to-end against the real local
backend via a Playwright walkthrough covering all three states) and the mobile "Copy watch link"
addition. Backend `./gradlew test` and `:shared:testDebugUnitTest` are green;
`:androidApp:assembleDebug` succeeds; `web-viewer`'s `npm run build` and `npm run test` (Vitest) are
green. Not yet deployed to Railway -- still points at local dev URLs (`WEB_VIEWER_ORIGINS`,
`NEXT_PUBLIC_API_BASE_URL`, `webViewerBaseUrl` all default to localhost); see the Hosting decision
above.

---

## 1. Overview

A public, no-login web page for watching a league's live auction — the thing a friend, family
member, or franchise supporter opens from a WhatsApp link, on any phone or laptop, with the app not
installed. `OVERVIEW.md` has scoped this as a thin Next.js app (`web-viewer/`, still a placeholder
folder) since the very first day of this rewrite: "thin SSE-consuming broadcast view — doesn't need
to share code with the mobile app."

The backend already carries almost everything this needs. `GET /api/v1/leagues/{id}`,
`GET /api/v1/leagues/{id}/auction/stream`, and `GET /api/v1/leagues/{id}/auction/results` are all
`permitAll()` today — built for the mobile app's own public-by-default league posture, but equally
usable by an anonymous browser. This phase is mostly a *new frontend consumer* of an existing public
contract, not a new backend feature.

---

## 2. Features

- One public URL per league (`/leagues/{id}`) — no login, no account, works for anyone with the
  link
- Renders one of three states depending on the league's `auctionStatus`:
  - **Not started** — league info (name, city, dates, format) and a plain "auction hasn't started
    yet" notice
  - **In progress** — the player currently on the block, the current leading bid and franchise, a
    live ticker of recent bids on that player, and live per-franchise standings (squad size so far,
    purse remaining)
  - **Completed** — final per-franchise results: players won, prices paid, purse remaining, and a
    flag for any franchise that ended up below the configured squad minimum
- Updates live via the same Server-Sent Events stream the mobile app already consumes — no manual
  refresh
- A shared link produces a real preview card (league name/city, banner image where set) when pasted
  into WhatsApp or similar, via Open Graph metadata
- The mobile app's League Detail screen gets a second share action, "Copy watch link," producing
  this page's URL — additive to the existing `crichere://` app deep link (see PHASE3.md), for the
  case where the person receiving the link doesn't have the app installed

---

## 3. Decisions Made

| Decision | Rationale |
|---|---|
| **Scope is a single per-league page — no browse/discovery screen** | Confirmed explicitly (2026-09-13). This is a spectator link shared per-league, not a second app; browsing/searching leagues is the mobile Dashboard's job (PHASE2.md) and has no driver here. |
| **Bid ticker included** — a live feed of recent bids for whichever player is currently on the block | Confirmed explicitly (2026-09-13), against the initial recommendation to skip it for MVP. Scoped to the *current* player only (empties out the moment the organizer advances) rather than a full-auction history feed — reads like a real auction broadcast ("last few bids on this player"), needs no pagination, and raises no "how far back can a spectator see" question. |
| **Recent-bids ticker folded into the existing `AuctionStateResponse`**, not a new endpoint | The response already flows through both the direct-call return value and the SSE broadcast, and its own class doc already commits to "a stream event and a GET are byte-for-byte the same shape" — a new endpoint would break that invariant for no benefit. |
| **Hosting: Railway, same account as the backend** | Confirmed explicitly (2026-09-13), over Vercel. Reuses existing infra per `OVERVIEW.md`'s Infra Reuse Rule (no new cloud provisioning) — a second Railway service running `next start`. Free generated subdomain for now, same "authored now, upgrade later" posture as Phase 2/3's other deferred-custom-domain gaps. |
| **Mobile Share gets an additive "Copy watch link," not a replacement** | Confirmed explicitly (2026-09-13). The existing `crichere://` deep link (PHASE3.md) still works for anyone with the app installed; this adds the case it can't reach — sharing outside the app, to someone without it installed. Copies to clipboard rather than opening a second OS share sheet — simplest, matches the actual use case (pasting into a WhatsApp group). |
| **No player photo or cricket-role field shown** | Not a design choice — that data was never made public anywhere (`LeaguePlayerResponse` is `id`/`userId`/`name` only). The viewer shows what's honestly available rather than inventing fields; franchise logos *are* shown, since `LeagueFranchiseResponse.logoUrl` is already public. |
| **`EventSource` (native browser API), no client library** | Standard Web API with built-in auto-reconnect — matches this app's low-dependency bias and needs nothing beyond what every browser already ships. |
| **CORS added, scoped to exactly the three consumed routes, allowlisted origins only, no wildcard** | New — nothing needed CORS before this phase (the mobile Ktor client never triggers a browser preflight). Even though the underlying data is public, an open `*` would let any third-party site embed/scrape the API from a browser at zero benefit — see Security below. |

---

## 4. Open Questions and Gaps

- None outstanding — see Security below for the two genuinely new risk areas this phase introduces, both resolved with a concrete mitigation rather than left open.

---

## 5. Pure Technical Things

### Data model

No new tables or columns beyond one addition to an existing response shape:
- `AuctionStateResponse` gains `recentBids: List<AuctionBidTickerResponse>` (`franchiseId`,
  `franchiseName`, `amount`, `placedAt`) — sourced from the existing `auction_bids` table
  (PHASE5.md), filtered to the current player and `reversed = false`, capped to the most recent 8,
  newest first. Empty whenever no player is currently open.

### API surface (additions)

- `GET /api/v1/leagues/{id}/auction/stream` and the `AuctionStateResponse` body of every mutating
  auction endpoint (PHASE5.md) now also carry `recentBids` — no new route.
- No other new backend routes. `GET /api/v1/leagues/{id}` and
  `GET /api/v1/leagues/{id}/auction/results` are reused as-is.

### Frontend

- `web-viewer/`: Next.js (App Router, TypeScript). `app/leagues/[id]/page.tsx` is a server
  component — `fetch()`s the league server-side for a fast first paint and for `generateMetadata`
  (Open Graph title/description from the league's real name/city, image from `bannerUrl` falling
  back to `logoUrl`). A client component (`LiveAuction.tsx`) owns the live state: starts from the
  server-rendered snapshot, opens `EventSource` against `/auction/stream`, replaces state on every
  event, and fetches `/auction/results` once `auctionStatus === "COMPLETED"`.
- An unknown/deleted league id (`GET /leagues/{id}` 404) renders the App Router's `notFound()` page.
- Currency renders via `Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" })`
  throughout — real Indian Rupee grouping, matching every other money field in this app.
- View-state derivation (which of the three states, ticker row shape, formatting) lives in pure
  functions (`lib/auction.ts`), kept separate from components so they're unit-testable without a
  DOM.

### Security

Analysis done 2026-09-13. This phase adds no new write path, no new auth surface, and no new data
disclosure (everything rendered was already public via `GET /leagues/{id}` or the Phase 5 auction
endpoints, which PHASE5.md's own Security section already reviewed). Two things are genuinely new:

**CORS is the actual new attack surface.** Before this phase, a browser script on another origin
couldn't read these endpoints even though they're public GETs, because no
`Access-Control-Allow-Origin` header exists — CORS is enforced by the browser, so curl/native
clients (including the mobile app) were never affected either way. Adding CORS support makes these
three routes browser-readable cross-origin for the first time, so the allowlist must be scoped
narrowly: a configurable, comma-separated list of real origins (env var `WEB_VIEWER_ORIGINS`,
including `http://localhost:3000` in dev), `GET` only, exactly the three already-`permitAll()`
routes (`/leagues/{id}`, `/leagues/{id}/auction/stream`, `/leagues/{id}/auction/results`) and no
others, `allowCredentials = false` (the viewer is fully anonymous — no bearer token or cookie ever
crosses this boundary). No wildcard — an open `*` would let any third-party site embed or scrape
this API from a browser at zero benefit to Crichere.

**Wider practical reach of the already-public SSE stream.** PHASE5.md's own Security section already
accepted that `stream`/`results` are unauthenticated and unrate-limited, with a single-instance
in-process `SseEmitter` registry and a 15s heartbeat as the known ceiling. This phase doesn't change
that trust boundary, but it does change *practical reach*: a `crichere://` link only works for
someone with the app installed, while an `https://` link is one tap from any chat app, so a given
league's stream can realistically attract more concurrent anonymous connections than before. No code
change proposed for this phase — flagged as the thing to watch if a league's viewer count ever gets
large; the existing heartbeat/single-instance limitation is the known ceiling, not a new one.

**No secrets, no credentials.** The Next.js app holds no server-side credential — its only
configuration is `NEXT_PUBLIC_API_BASE_URL`, which points at an already-public API base and is
intentionally public (baked into the client bundle) by Next.js's own `NEXT_PUBLIC_*` convention.
