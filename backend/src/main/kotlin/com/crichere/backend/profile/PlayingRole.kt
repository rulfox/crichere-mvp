package com.crichere.backend.profile

/**
 * A player's primary role on the field. String values must match the `playing_role` CHECK
 * constraint in V2__create_profiles_table.sql exactly.
 */
enum class PlayingRole {
    BATSMAN,
    BOWLER,
    ALL_ROUNDER,
    WICKETKEEPER,
}
