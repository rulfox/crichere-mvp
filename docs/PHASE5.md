# Phase 5 — Live Auction Engine

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions, and [PHASE4.md](PHASE4.md) for the base-price/purse/squad-size setup this phase runs against.

**Last updated:** 2026-09-13
**Status:** implemented (backend + Android). Backend `./gradlew test` (unit + Testcontainers
integration, including a real two-thread concurrent-bid race test) and mobile
`:shared:testDebugUnitTest` + all 3 iOS test-compile targets are green; `androidApp:assembleDebug`
succeeds. iOS `AuctionLiveView.swift` is authored but unwired, per the standing iOS posture (see
docs/PHASE2.md Section 5). Still open: real on-device Android walkthrough (see Deviations below for
what changed from this doc's original draft data model during implementation).

### Deviations from this doc's draft (Section 5), decided during implementation planning

- **No `auction_current_round` column.** `next-player` just picks uniformly at random among
  still-`PENDING` players each time it's called, rather than pre-computing and persisting a
  shuffled queue -- the same "random order, unsold comes back around" behavior falls out with no
  extra state to keep consistent, and nothing in the spec actually needs a round *number*.
- **Sold outcome lives on `league_players` directly** (`sold_to_franchise_id`/`sold_price`/
  `auction_outcome`), not a new per-auction join table -- matches this codebase's established
  preference for adding columns to an existing 1:1 row over a new table.
- **Undo needs one more bookkeeping column than drafted**: `auction_last_action_player_id`,
  alongside `auction_last_action_type`/`auction_last_action_bid_id` -- `auction_current_player_id`
  itself is cleared the moment a player closes (sold/unsold), so undoing that close needs its own
  pointer back to which player it was.
- **`GET /leagues/{id}/auction/stream`'s payload doubles as every mutating endpoint's response
  body** (`AuctionStateResponse`) -- not a separate shape -- so a client's in-memory state after a
  direct call and after a stream event are always identical.

---

## 1. Overview

This is the actual point of the app: franchises bidding live on players until every league configured in Phase 4 has a squad. Real-time state, money moving between a shared budget and individual franchises, and a live audience watching — nothing in this codebase has done real-time before now (it's pure REST end to end). This phase is deliberately scoped separately from Phase 4's setup work because it's a different order of complexity: a state machine, concurrent-bid safety, a broadcast mechanism, and organizer override/undo, none of which exist as patterns anywhere else in this app yet.

There is **no countdown timer**. The organizer plays the role of a real human auctioneer — advancing to the next player and calling each one sold or unsold entirely by their own judgment, exactly like an in-person auction. This single choice (see Decisions Made) removes an entire cluster of problems a timer would have introduced (a timer-duration setting, an "add time" override for disputes, and a pause-vs-end distinction) without losing anything real auctions need.

---

## 2. Features

- Organizer (acting as auctioneer) starts an auction once Phase 4's settings are configured and the readiness check passes (pool non-empty, at least one franchise present)
- Players are offered one at a time, in random shuffle order. The organizer manually advances to each next player when ready — nothing auto-starts the moment the previous one is closed out, so a break between players is free (the organizer just doesn't advance yet)
- Franchise owners place bids in real time; everyone watching (organizer, all franchise owners, anyone else viewing the league) sees the current player and current leading bid live
- The organizer manually closes out the current player: **"Sold"** (locks it in for whoever's currently leading, at their bid) or **"Unsold"** (if literally nobody bid) — whenever they judge bidding has stopped. No clock decides this.
- Organizer can undo/override a mistake (wrong bid, wrongly-marked sold player, reassignment)
- A franchise cannot bid on a player if doing so would push its squad past Phase 4's configured max headcount
- By default, a franchise cannot bid past its remaining purse. The organizer/auctioneer can toggle an "allow exceeding purse" switch live, mid-auction -- while it's on, franchises can bid beyond what's left in their purse. Any franchise over its purse must be visibly, obviously flagged everywhere its budget shows (live view, post-auction results) so nobody mistakes an over-limit bid for a normal one. The excess amount (how far over purse a franchise has gone) is tracked throughout. **Flagged for detailed design during this phase's own implementation planning, not fully specced yet** -- captured now (2026-09-12) so the idea isn't lost.
- Any player marked unsold is requeued for a later round; this repeats until either every player in the pool is sold (auction ends automatically) or the organizer manually ends it (remaining players marked UNSOLD)
- Post-auction: a per-franchise squad view (players bought + price paid + budget remaining) and a league-wide results view; any franchise that ended up below Phase 4's squad min is flagged in this view for the organizer/committee to handle manually
- Sale price is bookkeeping only — no payment-proof step per sold player, same "Crichere doesn't process payment" posture as everywhere else in this app; settlement between franchise and organizer happens entirely outside the app

---

## 3. Decisions Made

| Decision | Rationale |
|---|---|
| **Real-time broadcast via Server-Sent Events, not WebSocket/STOMP** | Confirmed explicitly (2026-09-12). The actual need is one-way: broadcast current auction state to everyone watching. Bids themselves still arrive as normal POST requests (request/response, easy to validate/reject). SSE gets the one-way broadcast over plain HTTP with no new infra dependency; a full bidirectional WebSocket/STOMP channel (message broker, connection lifecycle) would be real added infrastructure for a need that isn't actually bidirectional. |
| **No countdown timer at all — organizer manually calls each player Sold/Unsold by judgment** | Reversed same-day (2026-09-12). The original plan (a going-once/twice/sold countdown) needed a timer-duration setting, an "add time" override for disputes, and raised an open question about pausing mid-countdown — three real problems that only exist *because* a timer exists. Removing it dissolves all three at once, and matches how a real human auctioneer actually runs a local cricket auction (their judgment *is* the countdown). |
| **Organizer manually advances to the next player** — nothing auto-starts | Confirmed explicitly, and what makes the timer removal work cleanly: a break between players is now free (organizer just doesn't advance), with no separate pause state needed to support it. |
| **Unsold players retry in later rounds; auction auto-ends when the pool is fully sold, otherwise the organizer manually ends it** | Confirmed explicitly. No fixed round limit (an arbitrary number with no real justification) and no auto-detect-a-quiet-round-and-stop (the organizer is in a better position to judge "this has genuinely stalled" than a heuristic). The happy path — everyone eventually sells — needs no organizer action at all. |
| **Organizer has undo/override power** | Confirmed explicitly. A live, judgment-driven bidding session with real money will produce real mistakes (wrong bid entered, wrong player advanced, closed out too early) that need a correction path, not a "restart the whole auction" fallback. |
| **Squad max is enforced live (hard block); squad min is a post-auction report only** | See PHASE4.md's Decisions Made — the underlying reasoning belongs there, the enforcement mechanism (a bid-time check for max, a results-view flag for min) belongs here. |
| **Sale price carries no payment-proof step** | Confirmed explicitly. Unlike the flat entry fee (one screenshot per franchise, ever), an auction can sell dozens of players — a proof step per sale would be real, repeated friction during a live, judgment-paced session for money that's already going to be settled offline regardless. |
| **Bid increment is organizer-configurable per league**, set on Phase 4's Auction Settings screen | Confirmed explicitly (2026-09-12). Belongs to that same pre-auction settings screen alongside base price/purse/squad min-max, even though the behavior it controls only happens here in Phase 5 -- Phase 4 owns configuration, Phase 5 owns the live behavior that consumes it. |
| **Auctioneer is always the league organizer** — no separate Auctioneer role, no co-organizer concept, for MVP | Confirmed explicitly (2026-09-13). `organizerUserId` stays a single column; every auction-control endpoint (start/next-player/sold/unsold/undo/end/toggle-exceed-purse) is organizer-only, same check as every other organizer-only action in this app. Explicitly a deferred scope decision, not a design that precludes it: role delegation (a distinct Auctioneer role, and/or co-organizers) is planned for a later phase once a real need shows up -- don't build a permission system for it now. |
| **Auction stream and live state are public**, same posture as the rest of the league | Confirmed explicitly (2026-09-13). Anyone who can already see the league (organizer, franchise owners, followers, the public) can watch the live auction read-only via the SSE stream. No separate visibility rule. |
| **Auction settings lock the moment the auction leaves `NOT_STARTED`** | Confirmed explicitly (2026-09-13) -- the retrofit PHASE4.md's own "editable indefinitely" decision already promised. `PUT /leagues/{id}/auction-settings` (Phase 4's endpoint) must reject once `auction_status != NOT_STARTED`. This is also why lowering `auction_squad_max` below an already-fulfilled franchise's won-player count can never happen: a franchise can only reach squad max *during* a running auction, by which point squad max is already frozen -- no separate check needed for that case. |
| **Joining/claiming locks the same way** — a player cannot join, a franchise cannot claim, once `auction_status != NOT_STARTED` | Confirmed explicitly (2026-09-13). Independent of (and in addition to) Phase 3's existing capacity-full gate (`playersRequired`/`franchisesRequired` reached) -- either condition alone already blocks new joins/claims; this adds "auction is running" as a second, separate reason to block them, since the pool is meant to be frozen once bidding starts. |
| **Roster removal (player leave/organizer-remove, franchise leave/organizer-dismiss) is also blocked once `IN_PROGRESS`** | Confirmed explicitly (2026-09-13). Removing a franchise or player mid-auction would leave `sold_to_franchise_id`/results-view rows dangling for no real benefit -- freeze the roster for the duration of a running auction, same reasoning as the settings lock. |
| **League completion is blocked while the auction is `IN_PROGRESS`** | Confirmed explicitly (2026-09-13). `PATCH /leagues/{id}/complete` throws a new exception (`AuctionInProgressException`-shape, mirroring existing organizer-action exceptions) if `auction_status == IN_PROGRESS` -- prevents an organizer from accidentally freezing awards/results on a half-finished auction. No relation the other way: completing a league has no effect on `NOT_STARTED` or `COMPLETED` auctions. |
| **No cap on how far a franchise can go over purse** while `auction_allow_exceed_purse` is on | Confirmed explicitly (2026-09-13). Matches the organizer-judgment philosophy already used for sold/unsold -- the toggle simply removes the purse check entirely rather than replacing it with a different, system-enforced limit. |
| **Toggling "allow exceed purse" back off re-enforces the purse check going forward, but does not retroactively touch an already-over-purse franchise's existing bids/excess amount** | Confirmed explicitly (2026-09-13). Once off, that franchise is blocked from any *further* bid that would keep it over (or push it further over) purse, exactly like a franchise that was never allowed to exceed it -- but its already-won players/already-accrued excess stay as a permanent, visible flag, not reset or hidden. |

---

## 4. Open Questions and Gaps

- **Concurrent-bid safety** is a real correctness requirement (two franchises bidding the same instant must not both "win") — this is an implementation detail to get right during planning (atomic check-and-set against the current highest bid), not a product decision, but flagged here so it doesn't get missed.

---

## 5. Pure Technical Things (as built)

### Data model

- `leagues` gained (V15__add_auction_engine.sql): `auction_status` (`NOT_STARTED`/`IN_PROGRESS`/`COMPLETED`), `auction_current_player_id` (nullable -- null between the organizer closing one player and advancing to the next), `auction_current_bid_amount`, `auction_current_leading_franchise_id`, `auction_allow_exceed_purse` (boolean, default false, toggled live by the organizer -- see Features), `auction_last_action_type`/`auction_last_action_bid_id`/`auction_last_action_player_id` (the one-shot undo pointer -- see Deviations above). No `auction_current_round` column and no timer-related column (see Deviations above and Decisions Made respectively).
- Excess-purse tracking is derived, not stored: `sum(won bids for franchise) - purse`, floored at zero, computed wherever a franchise's budget is displayed (`AuctionService.toResultResponse`), rather than a separately maintained running total that could drift.
- New `auction_bids` table: id, league_id, player_id, franchise_id, amount, placed_at, reversed — full history, not just the current-leading snapshot, for audit/dispute purposes; `undo` sets `reversed = true` rather than deleting the row.
- `league_players` gained the sold outcome directly (see Deviations above): `sold_to_franchise_id` (nullable), `sold_price` (nullable), `auction_outcome` (`PENDING`/`SOLD`/`UNSOLD`).

### API surface (as built)

- `POST /api/v1/leagues/{id}/auction/start` — organizer-only, runs the readiness check (Phase 4 settings complete, pool non-empty, at least one franchise) and **hard-blocks** if `auction_squad_max * (actual active franchise count) > (actual active pool size)` using real final counts (see PHASE4.md's squad-math decision — same inequality as that phase's save-time warning, just re-checked against real numbers instead of the `playersRequired`/`franchisesRequired` targets, and a hard block instead of a warning since nothing changes after this point), then sets `auction_status = IN_PROGRESS` with no current player yet (organizer must explicitly advance).
- `POST /api/v1/leagues/{id}/auction/next-player` — organizer-only, advances to the next (randomly shuffled, or requeued-unsold) player in the pool; fails if a current player is still open (must be closed via `sold`/`unsold` first).
- `GET /api/v1/leagues/{id}/auction/stream` — SSE endpoint, broadcasts current auction state (current player, current leading bid/franchise) to any connected client. No countdown value to broadcast.
- `POST /api/v1/leagues/{id}/auction/bids` — franchise-owner-only, places a bid on the current player; rejected if not higher than the current bid, or if it would exceed that franchise's squad max, or (unless `auction_allow_exceed_purse` is on) its remaining purse.
- `POST /api/v1/leagues/{id}/auction/toggle-exceed-purse` — organizer-only, flips `auction_allow_exceed_purse` live, mid-auction (flagged for detailed design later -- see Features).
- `POST /api/v1/leagues/{id}/auction/sold` — organizer-only, closes the current player, assigns to the current leading franchise at the current bid. Fails if there's no leading bid (use `unsold` instead).
- `POST /api/v1/leagues/{id}/auction/unsold` — organizer-only, closes the current player with no sale; requeues it for a later round.
- `POST /api/v1/leagues/{id}/auction/undo` — organizer-only, reverses the last bid or the last sold/unsold outcome.
- `POST /api/v1/leagues/{id}/auction/end` — organizer-only, manually ends the auction early.
- `GET /api/v1/leagues/{id}/auction/results` — the post-auction per-franchise squad view + league-wide results, including the squad-min flag.

### Security

Analysis done 2026-09-13, grounded in this codebase's existing patterns (JWT bearer auth via `SecurityConfig`'s `anyRequest().authenticated()` fallthrough, `requireOrganizer`/`ownerUserId` checks from `FranchiseService`, `ContentRateLimiter`'s per-user token buckets).

**Per-endpoint authorization:**
- `start`/`next-player`/`toggle-exceed-purse`/`sold`/`unsold`/`undo`/`end` — organizer-only (`requireOrganizer`, same as `updateAuctionSettings`). `start` must also re-run the readiness check (pool/franchise/squad-math) server-side, not just trust the UI already gated it.
- `bids` — authenticated, and the caller must be the *owner* of the franchise id in the request body (`franchise.ownerUserId != callerId` → 403, same shape as `FranchiseService`'s `NotFranchiseOwnerException`). This is the one endpoint with a body-supplied actor distinct from the caller — the classic IDOR shape. A franchise id that's real but belongs to a *different* league must be rejected too, same as `FranchiseService.findAwardOrThrow`'s "real but wrong parent" pattern — never trust a body-supplied id without scoping it to this league.
- `stream` (SSE) and `results` — public, no auth, matching `GET /leagues/{id}`'s existing posture; no new disclosure since the same state is already public.

**Concurrent-bid safety** (see Open Questions) is a correctness requirement that also doubles as an abuse-resistance one — this is the one place in the app where a race is directly exploitable for financial gain, not just a data-integrity bug. Two bids arriving the same instant must not both "win." Needs an optimistic-lock version check-and-increment in the same transaction as the bid write (retried/rejected on conflict) — not an in-process lock, which doesn't survive horizontal scaling (this app already documents that same limitation for its rate limiters), and not a bare check-then-write without a DB-level compare-and-set.

**Every "hard block" in this spec must be enforced server-side, not just UI-gated** — squad-max, bid-below-current-plus-increment, purse-exceeded-while-toggle-off, and the settings/roster/completion locks once `auction_status != NOT_STARTED` (see Decisions Made) all need to reject a direct API call that bypasses the client, not just hide the button.

**Rate limiting:**
- `bids` needs its own `ContentRateLimiter` bucket (`tryConsumeForBid`), keyed on caller `userId` like every other bucket there — unlike leave-request/follow (deliberately unlimited, no cost), a bid write touches a contended row under the concurrency fix above, so a flood from one account degrades the auction for everyone else, not just themselves.
- SSE being public and long-lived is new territory for this codebase (first real-time endpoint, no existing pattern to mirror) — needs a per-IP concurrent-connection cap or a max-connections-per-league ceiling to prevent connection-handling exhaustion; flagged as net-new infra work for implementation planning.

**Audit trail:** `auction_bids` storing full history (not just the current leading bid) is the right call for disputes — `undo` should write a compensating record rather than deleting the reversed row, so "who actually won this player" always has a real trail.
