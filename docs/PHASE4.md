# Phase 4 — Auction Setup & Readiness

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions, and [PHASE3.md](PHASE3.md) for the join/claim/franchise model this phase builds on.

**Last updated:** 2026-09-12
**Status:** implemented (backend + Android). Backend `./gradlew test` and mobile
`:shared:testDebugUnitTest` + all 3 iOS test-compile targets are green; `androidApp:assembleDebug`
succeeds. iOS `AuctionSettingsView.swift` is authored but unwired, per the standing iOS posture
(see Section 5's cross-reference to PHASE2.md). Still open: real on-device Android walkthrough (no
emulator/device in this environment) -- compile+unit-test verification only.

---

## 1. Overview

Phase 3 got players joining and franchise owners claiming. Neither phase touches the actual point of this whole app: an auction where franchises bid on players. Phase 4 is everything about an auction that is **pure setup and configuration** — the base price, the budget every franchise gets, squad-size rules, and the readiness check before an auction can start. No bidding happens in this phase. No real-time anything. It's CRUD, testable the normal way.

The live bidding mechanism itself — the state machine, real-time broadcast, undo, concurrent-bid safety — is a different order of complexity and ships as its own phase (see [PHASE5.md](PHASE5.md)). Bundling both into one phase risks the same scope-sprawl Phase 3 already showed signs of; splitting keeps each phase's testing story sane and ships something real (organizers can configure an auction) before the hardest part is even built.

---

## 2. Features

- Organizer sets, per league, on a dedicated Auction Settings screen: a flat base price (same for every player in the auction), a flat total purse (same for every franchise), a squad size range (min/max headcount per franchise), and a bid increment -- the last one is consumed live in Phase 5, but configured here alongside everything else
- Auction pool is derived, not separately curated: every currently-active joined player in the league is in the pool — no extra "select for auction" step
- A read view of the auction pool (who's in it) and each franchise's starting purse, for organizer visibility before auction day
- (Readiness check itself — "can this auction actually start" — lives in Phase 5, since that's the action it gates; this phase only makes sure the settings it validates actually exist)

---

## 3. Decisions Made

| Decision | Rationale |
|---|---|
| **Base price is one flat, league-wide number** — not per-player | Simplest possible model. A per-player base price (e.g. higher for a known better player) is a real feature some real auctions have, but it's organizer-judgment-heavy and not needed to make an auction functional. |
| **Purse is one flat, league-wide number** — every franchise gets the same budget | Matches the base-price reasoning exactly. Custom-per-franchise budgets are a real ask for later if it comes up, not needed now. |
| **Auction pool = every active joined player, automatically** — no separate selection/curation step | Phase 3 already has a real "joined" concept (`league_players`, active = not removed/left). Re-curating a second list on top would be duplicate state that can drift from the first. |
| **Squad size has both a min and a max, enforced differently** | **Max is a hard live rule** (Phase 5): a franchise literally cannot place a bid once buying would push it past max headcount — checkable at bid time, no ambiguity. **Min cannot be a live rule** — a franchise might have money left but nothing left worth bidding on, or run out of budget before reaching it. Min is a **post-auction report only** (Phase 5's results view flags any franchise that ended up below min) for the organizer/committee to handle manually — the system can't force a sale nobody wants to make. |
| **Auction settings stay editable by the organizer right up until the auction actually starts** — not locked once a franchise claims | Reversed same-day (2026-09-12): the earlier lean was to lock these once any franchise claims, mirroring Phase 3's capacity/fee lock. But that lock exists in Phase 3 to protect a franchise's expectations about numbers the *app* enforces. Here, no money ever moves through Crichere — purse/base-price are just reference numbers; the real commitment is whatever the organizing committee and franchise owner agree offline (same reasoning already used for skipping an in-app franchise-eligibility payment gate). Locking the field protects nothing real, so it stays open until Phase 5's "start auction" action locks it (a running auction obviously can't have its purse change mid-bid). |
| **No franchise-eligibility payment gate at auction time** | Franchise fee is paid and vetted at claim time (Phase 3, already shipped) — settled by the organizing committee offline before auction day. The app never re-checks payment status before allowing a franchise to bid; re-gating it here would be duplicate, out-of-band enforcement of something already handled upstream. |
| **Settings live on a dedicated Auction Settings screen, reached from League Detail** — not bolted onto League Creation/Edit | Confirmed explicitly (2026-09-12). Keeps the already-shipped League Creation form untouched; these fields are meaningless to most leagues until they're actually planning an auction, weeks after creation. |
| **A franchise ending below squad min is informational only** — no blocking consequence | Confirmed explicitly. Blocking something (e.g. league completion) needs a whole remediation path nobody's asked for; the results view just flags it for the organizer/committee to handle manually. |
| **Bid increment is organizer-configurable per league**, set here alongside base price/purse/squad min-max | Confirmed explicitly — see PHASE5.md's Decisions Made for the full reasoning (configured in Phase 4, consumed live in Phase 5). |
| **There is no per-player countdown timer at all** — Phase 5 has no timer duration to configure, so this phase has no timer field either | Reversed same-day (2026-09-12): a live per-player countdown was the original plan, but it turned out to need a timer-duration setting, an "add time" override, and a whole pause-vs-end question -- three problems that only exist because a timer exists. Removing the timer (organizer manually calls each player sold/unsold by judgment, exactly like a real human auctioneer) dissolves all three at once. See PHASE5.md's Decisions Made. |
| **Auction pool/purse view is public**, same as everything else in this app | Confirmed explicitly (2026-09-12). No separate visibility rule needed. |
| **Auction Settings and the pool/purse read view are one screen, not two** | Confirmed explicitly. The read view's content (a headcount, a repeated purse number) is small enough to just sit at the bottom of the same Settings screen. |
| **Settings get basic coherence validation**: `squad_min <= squad_max`, and base price/purse/bid increment must all be positive | Straightforward correctness, not a product decision — same bean-validation-style checks every other save endpoint in this app already has. |
| **Squad math gets a two-stage check, not one** | **At save time** (this phase): warn-only, checked against Phase 2's `playersRequired`/`franchisesRequired` *target* numbers (`squad_max * franchisesRequired` shouldn't exceed `playersRequired`) — these are aspirational and can still change before auction day, so a hard block here could reject a perfectly fine in-progress state. **At auction start** (Phase 5's readiness gate): hard block, re-checked against the *actual* final active player/franchise counts — nothing changes after this point, so it's the last real chance to catch an impossible configuration before bidding begins. This also answers whether squad size relates to Phase 2's capacity fields at all: yes, `squad_max` is cross-checked against them; `squad_min` is not (it only ever produces the informational post-auction flag, so it doesn't need a pre-check). |

---

## 4. Open Questions and Gaps

Every item raised during scoping (2026-09-12) was resolved by explicit decision — see Decisions Made. Nothing currently open for this phase.

---

## 5. Pure Technical Things (draft, pending implementation planning)

### Data model

- `leagues` gains: `auction_base_price` (numeric, nullable — null until configured), `auction_purse` (numeric, nullable), `auction_squad_min` (int, nullable), `auction_squad_max` (int, nullable), `auction_bid_increment` (numeric, nullable). No timer field -- there is no timer (see Decisions Made).
- No new tables in this phase — the pool is a read-time query (`league_players` where active), not a stored list.

### API surface (as built)

- `PUT /api/v1/leagues/{id}/auction-settings` — organizer-only to write, but the settings themselves are visible to anyone who can see the league (embedded in the existing public `GET /leagues/{id}` response, same as capacity/fees today, via the 6 new `LeagueResponse` fields). Editable anytime up until the auction starts (see Decisions Made) — Phase 5's `start` action is what locks it, not a franchise claiming. Rejects `squad_min > squad_max` (`SQUAD_SIZE_INVALID`, 400) or any non-positive number (bean validation, `VALIDATION_FAILED`, 400); the response's `auctionSquadMaxWarning` boolean is `true` (never a rejection) when `squad_max * franchisesRequired > playersRequired`.
- **No separate `GET /auction-pool` endpoint was built.** `LeagueResponse` already embeds `players`/`franchises` (Phase 3) — that list *is* the auction pool, and `auctionPurse` is the same number for every franchise. The Auction Settings screen (Android/iOS) renders its pool/purse view from the already-loaded `GET /leagues/{id}` response instead of a second call. (This corrects the endpoint this section originally sketched before implementation — see the implementation plan for the full reasoning.)
