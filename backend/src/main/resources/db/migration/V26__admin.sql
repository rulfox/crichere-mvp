-- Admin web (docs/PHASE20.md): sessions, audit attribution, internal accounts, insights storage.

-- Who did it. 'script' for backend/tools/moderation; the Google email for the admin web.
ALTER TABLE moderation_actions
    ADD COLUMN admin_email VARCHAR(320);

-- Admin web sessions (A4, G3). Only a SHA-256 of the cookie value is stored, so a database read
-- can't be replayed as a session. A row ends at expires_at, or at once when revoked.
CREATE TABLE admin_sessions (
    id          UUID PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    email       VARCHAR(320) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(300)
);
CREATE INDEX admin_sessions_email_live_idx ON admin_sessions (email) WHERE revoked_at IS NULL;

-- Owner, reviewer and test accounts, left out of every insight (G1).
ALTER TABLE users
    ADD COLUMN is_internal BOOLEAN NOT NULL DEFAULT FALSE;

-- One row per person per day they used the app (section 4). Written in batches, read nightly.
CREATE TABLE user_activity_days (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE NOT NULL,
    PRIMARY KEY (user_id, day)
);
CREATE INDEX user_activity_days_day_idx ON user_activity_days (day);

-- Daily summary written by the nightly job; charts read only this (section 4).
CREATE TABLE metrics_daily (
    day       DATE         NOT NULL,
    metric    VARCHAR(60)  NOT NULL,
    dimension VARCHAR(120) NOT NULL DEFAULT '',
    value     BIGINT       NOT NULL,
    PRIMARY KEY (day, metric, dimension)
);

-- Hand-entered chart annotations ("v1.0 on Play").
CREATE TABLE release_markers (
    id          UUID PRIMARY KEY,
    day         DATE         NOT NULL,
    label       VARCHAR(120) NOT NULL,
    created_by  VARCHAR(320) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Last runs of scheduled jobs, for the system health card (G4).
CREATE TABLE job_runs (
    id          UUID PRIMARY KEY,
    job         VARCHAR(60) NOT NULL,
    started_at  TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    detail      TEXT
);
CREATE INDEX job_runs_job_started_idx ON job_runs (job, started_at DESC);

-- Time-range reads for the nightly job: BRIN, tiny and near free to maintain on append-only rows.
CREATE INDEX users_created_at_brin ON users USING BRIN (created_at);
CREATE INDEX leagues_created_at_brin ON leagues USING BRIN (created_at);
CREATE INDEX league_players_joined_at_brin ON league_players USING BRIN (joined_at);
CREATE INDEX auction_bids_placed_at_brin ON auction_bids USING BRIN (placed_at);

-- Keyset paging for the admin league and user lists (sections 6, 7).
CREATE INDEX users_created_id_idx ON users (created_at DESC, id DESC);
CREATE INDEX leagues_created_id_idx ON leagues (created_at DESC, id DESC);
