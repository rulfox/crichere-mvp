-- League awards: a generic repeatable list (name, cash amount, trophy yes/no), not hardcoded
-- First/Second/Third columns -- see docs/PHASE2.md's Decisions Made for why. The mobile
-- creation flow pre-suggests three starter rows ("First Prize" etc.), but nothing here is
-- schema-special about them; "Man of the Match" added a month later is just another row.
--
-- Editable regardless of the owning league's status (no completion freeze, see
-- docs/PHASE2.md) -- some awards are only decided once a league is already complete.
CREATE TABLE league_awards (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id      UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    name           VARCHAR NOT NULL,
    cash_amount    NUMERIC,
    has_trophy     BOOLEAN NOT NULL DEFAULT false,
    display_order  INT NOT NULL
);
