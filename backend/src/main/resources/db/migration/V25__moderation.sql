-- Play compliance (docs/PHASE18.md): suspension, terms acceptance, blocks, reports, removed images.

ALTER TABLE users
    ADD COLUMN suspended_at TIMESTAMPTZ,
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_version VARCHAR(20);

-- One-way: a blocked person is never told, and nothing here is shown to them.
CREATE TABLE user_blocks (
    blocker_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT user_blocks_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX user_blocks_blocked_idx ON user_blocks (blocked_id);

-- reporter_id is cleared when the reporter's account is purged; the evidence stays for its retention
-- period. snapshot is what the target looked like at report time, built by the server, because the
-- target can change or delete it afterwards.
CREATE TABLE reports (
    id                UUID PRIMARY KEY,
    reporter_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    target_type       VARCHAR(10) NOT NULL CHECK (target_type IN ('USER', 'LEAGUE')),
    target_user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    target_league_id  UUID REFERENCES leagues (id) ON DELETE SET NULL,
    context_league_id UUID REFERENCES leagues (id) ON DELETE SET NULL,
    reason            VARCHAR(30) NOT NULL,
    details           VARCHAR(500),
    snapshot          TEXT NOT NULL,
    status            VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ
);
CREATE INDEX reports_open_idx ON reports (created_at) WHERE status = 'OPEN';
-- One open report per reporter and target: a second one is refused, not queued.
CREATE UNIQUE INDEX reports_one_open_user_idx ON reports (reporter_id, target_user_id)
    WHERE status = 'OPEN' AND target_type = 'USER';
CREATE UNIQUE INDEX reports_one_open_league_idx ON reports (reporter_id, target_league_id)
    WHERE status = 'OPEN' AND target_type = 'LEAGUE';

-- Audit trail of every team action. Written by backend/tools/moderation.
CREATE TABLE moderation_actions (
    id         UUID PRIMARY KEY,
    action     VARCHAR(30) NOT NULL,
    report_id  UUID REFERENCES reports (id) ON DELETE SET NULL,
    user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    league_id  UUID REFERENCES leagues (id) ON DELETE SET NULL,
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One-time notice to the owner of a removed image.
CREATE TABLE moderation_notices (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind         VARCHAR(20) NOT NULL CHECK (kind IN ('PHOTO', 'LEAGUE_LOGO', 'LEAGUE_BANNER', 'FRANCHISE_LOGO')),
    subject_name TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    dismissed_at TIMESTAMPTZ
);
CREATE INDEX moderation_notices_pending_idx ON moderation_notices (user_id) WHERE dismissed_at IS NULL;

-- A purge frees the phone number, so a suspended account's lookup hash is kept here and sign-up with
-- it is refused. Disclosed in the Privacy Policy.
CREATE TABLE suspended_phone_hashes (
    phone_lookup_hash TEXT PRIMARY KEY,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Set by the team when an image is removed, so every viewer gets the neutral placeholder.
ALTER TABLE profiles ADD COLUMN photo_removed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE leagues
    ADD COLUMN logo_removed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN banner_removed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE league_franchises ADD COLUMN logo_removed BOOLEAN NOT NULL DEFAULT FALSE;
