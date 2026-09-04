-- Grounds: a shared, reusable venue entity (see docs/PHASE2.md's Decisions Made) -- many
-- leagues can reference the same ground rather than each re-entering/re-pinning the same
-- physical location. No update/delete in Phase 2 -- a mis-registered ground is corrected by
-- registering a new one, not edited (see docs/PHASE2.md's Open Questions/Decisions).
--
-- Deleting the registering user cascades to their registered grounds, same convention as
-- profiles/refresh_tokens -- there is no account-deletion feature yet, so this is currently
-- unreachable in practice, kept only for schema consistency.
CREATE TABLE grounds (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR NOT NULL,
    country               VARCHAR(2) NOT NULL DEFAULT 'IN',
    state                 VARCHAR NOT NULL,
    district              VARCHAR NOT NULL,
    city                  VARCHAR NOT NULL,
    latitude              DOUBLE PRECISION NOT NULL,
    longitude             DOUBLE PRECISION NOT NULL,
    registered_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
