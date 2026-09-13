package com.crichere.app.league

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** In-memory [AuctionRepository] test double -- `commonTest` has no real backend/SSE to hit. */
class FakeAuctionRepository : AuctionRepository {

    var nextState: AuctionStateDto? = null
    var actionError: Throwable? = null
    val actionCalls = mutableListOf<String>()
    val placeBidRequests = mutableListOf<PlaceBidRequestDto>()
    val toggleExceedPurseCalls = mutableListOf<Boolean>()

    var nextResults: AuctionResultsDto? = null
    var resultsError: Throwable? = null

    /** Defaults to never emitting -- tests that care about stream events replace this, typically with a replay-1 `MutableSharedFlow` so a subscribe-after-emit still sees the value. */
    var stream: Flow<AuctionStateDto> = emptyFlow()

    override suspend fun start(leagueId: String): AuctionStateDto = action("start")
    override suspend fun nextPlayer(leagueId: String): AuctionStateDto = action("next-player")

    override suspend fun placeBid(leagueId: String, request: PlaceBidRequestDto): AuctionStateDto {
        placeBidRequests += request
        return action("bid")
    }

    override suspend fun sold(leagueId: String): AuctionStateDto = action("sold")
    override suspend fun unsold(leagueId: String): AuctionStateDto = action("unsold")
    override suspend fun undo(leagueId: String): AuctionStateDto = action("undo")

    override suspend fun toggleExceedPurse(leagueId: String, allow: Boolean): AuctionStateDto {
        toggleExceedPurseCalls += allow
        return action("toggle-exceed-purse")
    }

    override suspend fun end(leagueId: String): AuctionStateDto = action("end")

    override suspend fun getResults(leagueId: String): AuctionResultsDto {
        resultsError?.let { throw it }
        return nextResults ?: error("nextResults not stubbed")
    }

    override fun streamAuctionState(leagueId: String): Flow<AuctionStateDto> = stream

    private fun action(name: String): AuctionStateDto {
        actionCalls += name
        actionError?.let { throw it }
        return nextState ?: error("nextState not stubbed")
    }
}
