-- Leagues: discovery/creation only in Phase 2, no auction mechanics (see docs/PHASE2.md).
--
-- Name/state/district/city/starts_on are NOT NULL -- League Creation is one continuous form
-- with a single Save action, no resumable partial-save like Profile Setup's onboarding
-- (confirmed during implementation, see docs/PHASE2.md's Decisions Made), so these are required
-- in one sitting to create a league at all. That makes a "DRAFT, not yet announced" status
-- genuinely unreachable, not just rare -- so status is derived purely from completed_at
-- (NULL = ANNOUNCED, set = COMPLETED), not a stored/enumerated column.
--
-- ground_id is nullable and deliberately NOT hard-linked to this row's own state/district/city
-- (a league can, unusually, reference a ground in a different area than its declared location --
-- see docs/PHASE2.md's Decisions Made).
--
-- Deleting the organizing user cascades to their leagues, same convention as grounds/profiles/
-- refresh_tokens -- there is no account-deletion feature yet, kept only for schema consistency.
-- Deleting a ground sets ground_id to NULL on any league referencing it rather than cascading --
-- a ground disappearing should never silently delete someone else's league.
CREATE TABLE leagues (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                 VARCHAR NOT NULL,
    description          TEXT,
    logo_url             TEXT,
    banner_url           TEXT,
    country              VARCHAR(2) NOT NULL DEFAULT 'IN',
    state                VARCHAR NOT NULL,
    district             VARCHAR NOT NULL,
    city                 VARCHAR NOT NULL,
    ground_id            UUID REFERENCES grounds(id) ON DELETE SET NULL,
    starts_on            DATE NOT NULL,
    format               VARCHAR,
    franchises_required  INT,
    players_required     INT,
    franchise_fee        NUMERIC,
    player_fee           NUMERIC,
    completed_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
