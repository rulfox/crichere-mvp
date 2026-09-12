-- A player's join row against a league's players_required capacity (see docs/PHASE3.md).
--
-- payment_screenshot_url is only null when the league has no player_fee set at join time --
-- enforced in PlayerService (it reads a sibling table's column), not a CHECK constraint here.
--
-- leave_requested_at/removed_at implement the request-and-approve leave flow: a self-requested
-- leave sets leave_requested_at; an organizer approval or a unilateral Remove sets removed_at
-- (one field covers both -- the effect on capacity is identical either way, and nothing needs to
-- distinguish "left" from "removed" after the fact).
--
-- Deleting the league or the joining user cascades -- an orphaned join row with no league is
-- never valid, and there is no account-deletion feature yet (kept only for schema consistency,
-- same convention as grounds/leagues/profiles/refresh_tokens' own users(id) references).
CREATE TABLE league_players (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id              UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    payment_screenshot_url TEXT,
    leave_requested_at     TIMESTAMPTZ,
    removed_at             TIMESTAMPTZ
);

-- Double-submit protection (see docs/PHASE3.md): at most one *active* (removed_at IS NULL) row
-- per (league, user). A partial unique index, not a table-level UNIQUE constraint, because the
-- uniqueness rule only applies to active rows -- a user who left and later rejoins needs a second
-- row, not a rejected insert.
CREATE UNIQUE INDEX league_players_active_unique ON league_players (league_id, user_id) WHERE removed_at IS NULL;
