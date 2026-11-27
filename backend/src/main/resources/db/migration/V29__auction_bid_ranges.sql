-- Bid steps (see docs/PHASE22.md). Optional ranges with bigger steps at higher prices, stored as
-- `upTo:step` pairs joined by commas (e.g. `1000:250,3000:500`) -- each range starts where the previous
-- one ended (the base price for the first). The existing auction_bid_increment stays as the step for
-- everything above the last range, so an empty value here is exactly the old flat increment and no
-- existing league changes.
ALTER TABLE leagues ADD COLUMN auction_bid_ranges TEXT NOT NULL DEFAULT '';
