-- Admin email alerts (docs/PHASE20.md Step 4). One row per alert kind, locked by the job that sends it,
-- so a restart or a second instance can neither lose nor repeat an alert.
CREATE TABLE alert_state (
    kind         VARCHAR(40) PRIMARY KEY,
    -- When the last email went out; the next can't go before this plus 15 minutes.
    last_sent_at TIMESTAMPTZ,
    -- Everything created up to here has been counted in an email already.
    watermark    TIMESTAMPTZ NOT NULL
);

-- Start from now: reports from before the admin web existed were handled with the script.
INSERT INTO alert_state (kind, watermark) VALUES ('NEW_REPORTS', now());
