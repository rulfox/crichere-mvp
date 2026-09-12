-- Following a league: no fee, no capacity, no proof, no leave-approval flow -- a plain
-- many-to-many join row. Composite primary key, no surrogate id -- nothing ever needs to
-- reference a single follow row by its own id, only by the (league, user) pair.
CREATE TABLE league_follows (
    league_id    UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (league_id, user_id)
);
