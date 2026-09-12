package com.crichere.app.league

/** In-memory [MyLeaguesRepository] test double -- `commonTest` has no real backend to hit. */
class FakeMyLeaguesRepository(
    var nextResult: MyLeaguesDto = MyLeaguesDto(),
) : MyLeaguesRepository {

    var error: Throwable? = null
    var callCount = 0
        private set

    override suspend fun getMyLeagues(): MyLeaguesDto {
        callCount++
        error?.let { throw it }
        return nextResult
    }
}
