-- Refresh-token reuse handling (docs/SECURITY-AUDIT.md Finding 5). Each rotated token points at the
-- token issued in its place, so the backend can tell a lost reply (the old token comes back within a
-- short grace window while its successor is still unused) from a replayed, stolen token (anything
-- else), which revokes every session of that user.
ALTER TABLE refresh_tokens
    ADD COLUMN replaced_by_id UUID NULL REFERENCES refresh_tokens(id) ON DELETE SET NULL;
