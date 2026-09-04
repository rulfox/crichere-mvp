-- Users: one row per authenticated phone number. The phone number itself is never stored
-- in plaintext; phone_lookup_hash is a deterministic (HMAC-style) hash used for lookup by
-- login, phone_encrypted is a reversible encrypted value used when the plaintext phone is
-- needed (e.g. for display or SMS). Both are populated by application code in a later task.
CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    phone_lookup_hash  VARCHAR NOT NULL UNIQUE,
    phone_encrypted    TEXT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
