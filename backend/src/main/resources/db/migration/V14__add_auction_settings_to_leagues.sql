-- Auction setup fields (see docs/PHASE4.md) -- a flat base price/purse (same number for every
-- player/franchise respectively), a squad headcount range, and a bid increment. Nullable, same
-- posture as franchise_fee/player_fee: a league can exist for weeks without ever configuring
-- these, only mandatory once Phase 5's "start auction" action actually needs them.
ALTER TABLE leagues ADD COLUMN auction_base_price   NUMERIC;
ALTER TABLE leagues ADD COLUMN auction_purse        NUMERIC;
ALTER TABLE leagues ADD COLUMN auction_squad_min    INT;
ALTER TABLE leagues ADD COLUMN auction_squad_max    INT;
ALTER TABLE leagues ADD COLUMN auction_bid_increment NUMERIC;
