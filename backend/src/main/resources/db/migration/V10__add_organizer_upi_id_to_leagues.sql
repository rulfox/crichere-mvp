-- Organizer's own UPI id for peer-to-peer fee collection (see docs/PHASE3.md) -- Crichere never
-- custodies funds, so this is just a payee identifier displayed to a joining player/franchise
-- owner, never validated/parsed server-side. Nullable at the schema level: "required once a fee
-- is set" is an application-layer rule (LeagueService), not a CHECK constraint, since it reads a
-- sibling column (franchise_fee/player_fee) -- same reasoning as every other cross-field
-- validation in this codebase (e.g. Profile's conditional bowling-style requirement).
ALTER TABLE leagues ADD COLUMN organizer_upi_id VARCHAR;
