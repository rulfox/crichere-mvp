package com.crichere.app.league

/** In-memory [RoleRepository] test double -- `commonTest` has no real backend to hit. */
class FakeRoleRepository : RoleRepository {

    var nextLookupResult: RoleLookupResultDto? = null
    var lookupError: Throwable? = null
    val lookupCalls = mutableListOf<Pair<String, String>>()

    var nextLeague: LeagueDto? = null
    var actionError: Throwable? = null
    val grantCalls = mutableListOf<String>()
    val revokeCalls = mutableListOf<String>()

    override suspend fun lookup(leagueId: String, phoneNumber: String): RoleLookupResultDto? {
        lookupCalls += leagueId to phoneNumber
        lookupError?.let { throw it }
        return nextLookupResult
    }

    override suspend fun grant(leagueId: String, userId: String): LeagueDto {
        grantCalls += userId
        actionError?.let { throw it }
        return nextLeague ?: error("nextLeague not stubbed")
    }

    override suspend fun revoke(leagueId: String, roleId: String): LeagueDto {
        revokeCalls += roleId
        actionError?.let { throw it }
        return nextLeague ?: error("nextLeague not stubbed")
    }
}
