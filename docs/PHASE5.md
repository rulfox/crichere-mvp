# Phase 5 — Live Auction Engine

Part of the Crichere full rewrite. See [OVERVIEW.md](OVERVIEW.md) for stack/infra decisions, and [PHASE4.md](PHASE4.md) for the base-price/purse/squad-size setup this phase runs against.

**Last updated:** 2026-09-12
**Status:** scoped (cofounder discussion), not yet through implementation planning.

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

---

## 4. Open Questions and Gaps

- **Concurrent-bid safety** is a real correctness requirement (two franchises bidding the same instant must not both "win") — this is an implementation detail to get right during planning (atomic check-and-set against the current highest bid), not a product decision, but flagged here so it doesn't get missed.
- **Auctioneer role** — is it always the league organizer personally driving the auction, or can they designate someone else (a real auctioneer distinct from the organizing owner) to run it? Leaning "always the organizer" for MVP (no new role/permission concept needed) — not yet confirmed.
- **Spectator visibility** — can anyone (followers, joined players not bidding, the public) watch the live auction read-only, or is it visible only to the organizer and franchise owners? Leaning "anyone who can already see the league can watch," matching the public-by-default posture leagues already have — not yet confirmed.

---

## 5. Pure Technical Things (draft, pending implementation planning)

### Data model

- New `auction_state` per league (or a dedicated `auctions` table if a league can only ever run one auction — leaning toward a column set on `leagues` itself: `auction_status` (`NOT_STARTED`/`IN_PROGRESS`/`COMPLETED`), `auction_current_player_id` (nullable -- null between the organizer closing one player and advancing to the next), `auction_current_bid_amount`, `auction_current_leading_franchise_id`, `auction_current_round`, `auction_allow_exceed_purse` (boolean, default false, toggled live by the organizer -- see Features)). No timer-related column -- there is no timer (see Decisions Made).
- Excess-purse tracking (the allow-exceed-purse feature, flagged for detailed design later): likely derived, not stored -- `sum(won bids for franchise) - purse`, floored at zero, computed wherever a franchise's budget is displayed, rather than a separately maintained running total that could drift.
- New `auction_bids` table: id, league_id, player_id (references the `league_players` row being auctioned), franchise_id, amount, placed_at — full history, not just the current-leading snapshot, for audit/dispute purposes.
- `league_players` (or a new per-auction join row) gains the sold outcome: `sold_to_franchise_id` (nullable), `sold_price` (nullable), `auction_outcome` (`PENDING`/`SOLD`/`UNSOLD`).

### API surface (draft)

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

- Bid placement needs the same per-user rate limit precedent every other mutating endpoint in this app has, plus a check that the caller is the bidding franchise's own owner.
- Concurrent-bid safety (see Open Questions) is a correctness requirement that also doubles as an abuse-resistance one — without it, a race could let a franchise "win" a bid it shouldn't have.
