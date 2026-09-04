-- Refresh tokens: one row per issued refresh token. Only a hash of the token is stored
-- (token_hash), never the plaintext token, so a database leak alone can't be used to mint
-- sessions. revoked_at is nullable and set (not deleted) when a token is invalidated, so
-- revocation history is preserved for audit/debugging.
--
-- Deleting a user cascades to their refresh tokens, for the same reason as profiles: user
-- deletion is a full account deletion, and a refresh token for a user that no longer exists
-- must never remain redeemable.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NULL
);
