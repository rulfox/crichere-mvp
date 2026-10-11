-- Account deletion (docs/PHASE16.md). A deleted account keeps its users row as a tombstone so the
-- leagues, bids and results other people still use keep pointing at something: every FK to
-- users(id) cascades, so a real DELETE would wipe those. The phone columns are cleared on purge,
-- which frees the number to sign up again as a new account (the unique index allows many NULLs).
ALTER TABLE users
    ADD COLUMN deletion_requested_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ALTER COLUMN phone_lookup_hash DROP NOT NULL,
    ALTER COLUMN phone_encrypted DROP NOT NULL;

-- The purge job's scan: requested, not yet purged.
CREATE INDEX users_pending_deletion_idx ON users (deletion_requested_at)
    WHERE deletion_requested_at IS NOT NULL AND deleted_at IS NULL;

-- Set when a purge hands a league to its earliest co-organizer, so the new organizer's league
-- page can explain the change.
ALTER TABLE leagues ADD COLUMN organizer_handover_at TIMESTAMPTZ;
