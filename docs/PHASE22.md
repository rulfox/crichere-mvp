# Phase 22: Bid steps

Organizers can make the bid step grow with the price, IPL style: small steps while bids are cheap,
bigger ones higher up. Design canvas: https://claude.ai/artifact/VzqU5W25rBT9RXVu7DsLUk (S1-S4 settings,
L1-L3 live bid dock).

## Rules

- **Ranges are optional.** Each range has an **Up to** and a **Step**. A range starts where the one above
  ended; the first starts at the base price. The organizer never types a start, so gaps and overlaps
  can't happen.
- **The "and above" step is required.** It is the existing `auction_bid_increment`, so a league with no
  ranges behaves exactly as before (one flat step).
- Each range's width (Up to − start) divides evenly by its step, so every boundary is itself a bid.
- Each step is bigger than the one before; the "and above" step is bigger than the last range's.
- Whole rupees only (base price, Up to, steps). At most 10 ranges.
- A range starts at its lower number: at exactly ₹1,000 in the example below, the step is ₹500.
- **Bids must be a step on the ladder.** Owners can still type any amount; anything else is rejected
  with the valid bids either side ("₹2,200 isn't a bid step. Try [₹2,000] [₹2,500]").
- The **minimum next bid** is the next ladder value above the current bid (the base price before any
  bid). An off-ladder current bid (from before this phase) snaps back onto the ladder.
- Like every other auction setting, the steps are **locked once the auction starts**.

Example: base ₹250; up to ₹1,000 step ₹250; up to ₹3,000 step ₹500; up to ₹15,000 step ₹1,000; above
step ₹2,500. Bids go 250, 500, 750, 1,000, 1,500 … 3,000, 4,000 … 15,000, 17,500, 20,000 …

## Where the rule lives

The ladder is computed in two places, kept identical with the same test table:

- Backend `auction/BidLadder.kt` (authority: every bid is re-checked).
- Shared `league/BidLadder.kt`, so the phone shows the minimum, the "+" step and the suggestions
  instantly, with no extra round trip. Settings are locked during the auction, so the phone's copy of
  the ranges can't go stale.
- `backend/e2e/lib/ladder.mjs` mirrors it for the test bidder scripts.

## API

- `PUT /leagues/{id}/auction-settings` takes `bidRanges: [{upTo, step}]`. `bidIncrement` is the "and
  above" step. **`bidRanges` omitted (an app from before this phase) keeps the saved ranges**; an empty
  list clears them. Broken rules: `400 BID_RANGES_INVALID` with `rangeIndex` (0-based; absent for the
  "and above" step) and `reason`.
- League responses carry `auctionBidRanges: [{upTo, step}]` (empty when none). Older apps ignore it.
- `POST /leagues/{id}/auction/bids`: below the minimum is still `400 BID_TOO_LOW` (`minimumAmount`); a
  bid that isn't a step is `400 BID_NOT_ON_LADDER` with `lower` (absent if none at or above the
  minimum) and `upper`.
- Storage: `leagues.auction_bid_ranges` TEXT, `upTo:step` pairs joined by commas (`1000:250,3000:500`),
  empty by default (V29). The admin league page shows it as "250 / 500, then 2,500 pts".

## Older app versions

Leagues without ranges work on older apps unchanged. On a league with ranges an older app computes the
old flat minimum, so the server may reject its bids; those users need the update. The planned
minimum-app-version check (Feature 2) covers this once it ships.

## Status

- Backend: done; unit tests pass. Integration test added (`AuctionFlowIntegrationTest`), needs Docker
  to run.
- Shared: done; tests written, not yet run (needs the Android SDK; Kotlin/Native can't compile the
  module's existing test names).
- Android and iOS screens: written, not yet compiled (Android SDK and Xcode were not available).
- Admin web: written, not type-checked (no `node_modules`).
