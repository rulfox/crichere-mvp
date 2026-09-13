-- The live auction engine (see docs/PHASE5.md). Three additions:
--
-- 1. league_players gains the sold outcome. Reusing this existing table rather than a new join
--    row -- the sold outcome is 1:1 with an already-joined player, same "don't duplicate state"
--    reasoning docs/PHASE4.md already used for the auction pool itself.
-- 2. leagues gains the live auction state (status/current player/current bid/leading franchise/
--    exceed-purse toggle) plus a single "last action" pointer that `undo` consumes -- one level of
--    undo only, matching the spec's "reverses the last bid or the last sold/unsold outcome"
--    (singular), not a full history stack.
-- 3. auction_bids is new: full bid history for audit/dispute purposes (see docs/PHASE5.md's
--    Security section) -- a bid is never deleted, only flagged `reversed` by `undo`.
--
-- No persisted shuffle-order table: "random order, unsold comes back around" is produced by
-- picking uniformly at random among still-PENDING league_players rows each time the organizer
-- advances, so there's no queue state to keep consistent (see the Phase 5 implementation plan).

ALTER TABLE league_players ADD COLUMN sold_to_franchise_id UUID REFERENCES league_franchises(id) ON DELETE SET NULL;
ALTER TABLE league_players ADD COLUMN sold_price           NUMERIC;
ALTER TABLE league_players ADD COLUMN auction_outcome      VARCHAR(10) NOT NULL DEFAULT 'PENDING';

ALTER TABLE leagues ADD COLUMN auction_status                    VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED';
ALTER TABLE leagues ADD COLUMN auction_current_player_id         UUID REFERENCES league_players(id) ON DELETE SET NULL;
ALTER TABLE leagues ADD COLUMN auction_current_bid_amount        NUMERIC;
ALTER TABLE leagues ADD COLUMN auction_current_leading_franchise_id UUID REFERENCES league_franchises(id) ON DELETE SET NULL;
ALTER TABLE leagues ADD COLUMN auction_allow_exceed_purse         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE leagues ADD COLUMN auction_last_action_type           VARCHAR(10);
-- Which player a SOLD/UNSOLD last-action refers to -- auction_current_player_id itself is cleared
-- the moment that player closes, so undo needs its own pointer back to it.
ALTER TABLE leagues ADD COLUMN auction_last_action_player_id      UUID REFERENCES league_players(id) ON DELETE SET NULL;

-- Full bid history. `reversed` marks a bid undone by `undo` -- never deleted, so a post-auction
-- dispute always has a real trail (see docs/PHASE5.md's Security section).
CREATE TABLE auction_bids (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id    UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    player_id    UUID NOT NULL REFERENCES league_players(id) ON DELETE CASCADE,
    franchise_id UUID NOT NULL REFERENCES league_franchises(id) ON DELETE CASCADE,
    amount       NUMERIC NOT NULL,
    placed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reversed     BOOLEAN NOT NULL DEFAULT FALSE
);

-- Added after auction_bids exists, since it references it.
ALTER TABLE leagues ADD COLUMN auction_last_action_bid_id UUID REFERENCES auction_bids(id) ON DELETE SET NULL;
