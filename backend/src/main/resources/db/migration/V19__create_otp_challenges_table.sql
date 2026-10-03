-- OTP challenges for the backend-driven OTP provider (MSG91, docs/PHASE12.md): one row per
-- "send a code" request. The client only ever sees this row's id, never the provider's own
-- request id (provider_req_id), and the phone number used at verify time comes from this row,
-- never from the verify request -- so a code proven for one number cannot be spent on another.
--
-- Attempt and resend counters live here (not in memory) so a restart cannot be used to reset
-- the wrong-code budget. phone_encrypted is needed because first-time sign-in has to create the
-- user row, which stores the number encrypted; phone_lookup_hash is the same HMAC users uses.
--
-- No FK to users: a challenge exists before the account does (first sign-in).
CREATE TABLE otp_challenges (
    id                UUID PRIMARY KEY,
    phone_lookup_hash VARCHAR NOT NULL,
    phone_encrypted   TEXT NOT NULL,
    provider_req_id   VARCHAR NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    last_sent_at      TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    verify_attempts   INT NOT NULL DEFAULT 0,
    resends           INT NOT NULL DEFAULT 0,
    consumed_at       TIMESTAMPTZ NULL
);

-- "latest active challenge for this phone" (cooldown + supersede on a fresh send).
CREATE INDEX otp_challenges_phone_lookup_hash_idx ON otp_challenges (phone_lookup_hash, created_at DESC);

-- the sweep of long-dead rows done on each send.
CREATE INDEX otp_challenges_expires_at_idx ON otp_challenges (expires_at);
