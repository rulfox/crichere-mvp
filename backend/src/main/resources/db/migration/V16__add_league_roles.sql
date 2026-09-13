CREATE TABLE league_roles (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    league_id          UUID NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role               VARCHAR(20) NOT NULL,
    granted_by_user_id UUID NOT NULL REFERENCES users(id),
    granted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX league_roles_active_unique ON league_roles (league_id, user_id, role) WHERE revoked_at IS NULL;
