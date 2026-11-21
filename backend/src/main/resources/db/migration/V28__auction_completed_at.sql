-- When an auction finished, for "auctions completed a week" (docs/PHASE20.md Step 7). Set by
-- AuctionService when the auction reaches COMPLETED and cleared if an undo reopens it.
ALTER TABLE leagues ADD COLUMN auction_completed_at TIMESTAMPTZ;

-- Past auctions: the last bid is the closest record of when they ended.
UPDATE leagues l
SET auction_completed_at = coalesce((SELECT max(b.placed_at) FROM auction_bids b WHERE b.league_id = l.id), l.updated_at)
WHERE l.auction_status = 'COMPLETED';

CREATE INDEX leagues_auction_completed_at_idx ON leagues (auction_completed_at) WHERE auction_completed_at IS NOT NULL;
