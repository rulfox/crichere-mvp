package com.crichere.backend.player

/** No player row matches the given id under the given league -- also thrown (not a different error) when the id is real but belongs to a *different* league. */
class LeaguePlayerNotFoundException : RuntimeException("Player not found")

/** The caller already has an active join row for this league -- see `league_players_active_unique`. */
class AlreadyJoinedException : RuntimeException("You have already joined this league as a player")

/** An approve/dismiss was attempted on a player row with no pending leave request. */
class NoLeaveRequestPendingException : RuntimeException("No leave request is pending for this player")

/** The caller is not this player row's own user -- checked on a self-scoped leave request. */
sealed class PlayerAuthorizationException(message: String) : RuntimeException(message) {
    class NotPlayerOwnerException : PlayerAuthorizationException("You are not this player")
}

typealias NotPlayerOwnerException = PlayerAuthorizationException.NotPlayerOwnerException
