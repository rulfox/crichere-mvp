package com.crichere.backend.profile

/**
 * A player's bowling style. Not every player bowls, hence this is nullable on [ProfileEntity]
 * rather than required. String values must match the `bowling_style` CHECK constraint in
 * V2__create_profiles_table.sql exactly.
 *
 * This is the standard set of bowling styles used across cricket scoring/stats systems:
 * right/left arm, pace (fast/medium) or spin, with spin further split into the conventional
 * finger-spin deliveries (offbreak for right-arm, orthodox for left-arm) and wrist-spin
 * deliveries (legbreak for right-arm, chinaman for left-arm).
 */
enum class BowlingStyle {
    RIGHT_ARM_FAST,
    RIGHT_ARM_MEDIUM,
    RIGHT_ARM_OFFBREAK,
    RIGHT_ARM_LEGBREAK,
    LEFT_ARM_FAST,
    LEFT_ARM_MEDIUM,
    LEFT_ARM_ORTHODOX,
    LEFT_ARM_CHINAMAN,
}
