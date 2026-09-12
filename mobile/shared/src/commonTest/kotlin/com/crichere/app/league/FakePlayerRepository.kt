package com.crichere.app.league

/** In-memory [PlayerRepository] test double -- `commonTest` has no real backend to hit. */
class FakePlayerRepository : PlayerRepository {

    var nextJoined: LeaguePlayerDto? = null
    var joinError: Throwable? = null
    val joinRequests = mutableListOf<Pair<String, LeaguePlayerJoinRequestDto>>()

    var removeError: Throwable? = null
    val removeCalls = mutableListOf<Pair<String, String>>()

    var nextLeaveRequested: LeaguePlayerDto? = null
    var requestLeaveError: Throwable? = null

    var nextApproved: LeaguePlayerDto? = null
    var approveLeaveError: Throwable? = null

    var nextDismissed: LeaguePlayerDto? = null
    var dismissLeaveError: Throwable? = null

    override suspend fun join(leagueId: String, request: LeaguePlayerJoinRequestDto): LeaguePlayerDto {
        joinRequests += leagueId to request
        joinError?.let { throw it }
        return nextJoined ?: error("nextJoined not stubbed")
    }

    override suspend fun remove(leagueId: String, playerId: String) {
        removeCalls += leagueId to playerId
        removeError?.let { throw it }
    }

    override suspend fun requestLeave(leagueId: String, playerId: String): LeaguePlayerDto {
        requestLeaveError?.let { throw it }
        return nextLeaveRequested ?: error("nextLeaveRequested not stubbed")
    }

    override suspend fun approveLeave(leagueId: String, playerId: String): LeaguePlayerDto {
        approveLeaveError?.let { throw it }
        return nextApproved ?: error("nextApproved not stubbed")
    }

    override suspend fun dismissLeave(leagueId: String, playerId: String): LeaguePlayerDto {
        dismissLeaveError?.let { throw it }
        return nextDismissed ?: error("nextDismissed not stubbed")
    }
}
