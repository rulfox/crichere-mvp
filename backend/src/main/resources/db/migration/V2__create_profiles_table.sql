-- Profiles: one-to-one extension of users, keyed on user_id (no surrogate id). A user row
-- can exist without a profile row (freshly OTP-authenticated, first-time login not yet
-- completed) -- that's the "first-time cricket-player profile" flow this MVP phase covers.
--
-- Deleting a user cascades to their profile: user deletion is expected to be a full account
-- deletion, and an orphaned profile row with no owning user is never a valid state.
--
-- Note: there is no profile_complete column. Whether a profile is "complete" is derived on
-- read by application code (checking for NULLs in the required fields), not stored --
-- storing it would let it drift out of sync with the fields it's derived from.
CREATE TABLE profiles (
    user_id        UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    name           VARCHAR(100),
    photo_url      TEXT,
    country        VARCHAR(2) NOT NULL DEFAULT 'IN',
    state          VARCHAR,
    city           VARCHAR,
    playing_role   VARCHAR CHECK (playing_role IN ('BATSMAN', 'BOWLER', 'ALL_ROUNDER', 'WICKETKEEPER')),
    batting_style  VARCHAR CHECK (batting_style IN ('RIGHT_HAND', 'LEFT_HAND')),
    -- Standard bowling styles: right/left arm, pace/spin, with spin split into the
    -- conventional finger-spin (offbreak / orthodox) and wrist-spin (legbreak / chinaman)
    -- varieties. Not enumerated in the source spec -- chosen from cricket-domain knowledge.
    bowling_style  VARCHAR CHECK (bowling_style IS NULL OR bowling_style IN (
        'RIGHT_ARM_FAST',
        'RIGHT_ARM_MEDIUM',
        'RIGHT_ARM_OFFBREAK',
        'RIGHT_ARM_LEGBREAK',
        'LEFT_ARM_FAST',
        'LEFT_ARM_MEDIUM',
        'LEFT_ARM_ORTHODOX',
        'LEFT_ARM_CHINAMAN'
    ))
);
