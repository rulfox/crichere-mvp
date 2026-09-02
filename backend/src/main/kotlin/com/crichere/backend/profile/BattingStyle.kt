package com.crichere.backend.profile

/**
 * A player's batting handedness. String values must match the `batting_style` CHECK
 * constraint in V2__create_profiles_table.sql exactly.
 */
enum class BattingStyle {
    RIGHT_HAND,
    LEFT_HAND,
}
