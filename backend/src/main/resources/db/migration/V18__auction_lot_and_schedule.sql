-- Public web redesign (see docs/PHASE11.md, decisions D2/D3). Two additions to leagues:
--
-- 1. auction_lot_counter: +1 every time next-player opens a player, so the viewer can show
--    "Lot 14". Can't be derived from league_players outcomes -- an unsold player goes back to
--    PENDING and comes round again as a new lot, which erases the history a count would need.
-- 2. auction_scheduled_at: optional "bidding opens at" time the organizer sets alongside the
--    other auction settings. Informational only -- nothing starts the auction automatically.

ALTER TABLE leagues ADD COLUMN auction_lot_counter  INT NOT NULL DEFAULT 0;
ALTER TABLE leagues ADD COLUMN auction_scheduled_at TIMESTAMPTZ;
