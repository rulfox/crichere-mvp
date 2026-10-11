-- Account deletion, display side (docs/PHASE16.md, design D9/D10).
-- Set by the purge on the profile it reduces to "Deleted user", so every response that already
-- loads the profile can show the neutral avatar without a name-text match.
ALTER TABLE profiles ADD COLUMN account_deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- When the new organizer dismissed the "previous organizer deleted their account" notice. Only
-- the new organizer sees it, so one column per league is enough; a later handover clears it.
ALTER TABLE leagues ADD COLUMN organizer_handover_seen_at TIMESTAMPTZ;
