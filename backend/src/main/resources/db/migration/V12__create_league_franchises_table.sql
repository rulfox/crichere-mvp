-- A claimed franchise: a real named entity (name + optional logo), not just an identity flag on
-- the user -- see docs/PHASE3.md's Decisions Made. Same payment/leave/removal columns as
-- league_players, same cascade behavior on league_id/owner_user_id.
--
-- Deliberately NO uniqueness constraint on (league_id, owner_user_id): dual roles (a franchise
-- owner also joining as a player) and multi-franchise ownership by the same user are explicitly
-- allowed (see docs/PHASE3.md's Decisions Made) -- a unique index here would incorrectly reject a
-- legitimate second claim. Double-tap protection for this table is a client-side
-- disable-after-submit, not a DB constraint.
CREATE TABLE league_franchises (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id              UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    owner_user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                   VARCHAR NOT NULL,
    logo_url               TEXT,
    joined_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    payment_screenshot_url TEXT,
    leave_requested_at     TIMESTAMPTZ,
    removed_at             TIMESTAMPTZ
);
