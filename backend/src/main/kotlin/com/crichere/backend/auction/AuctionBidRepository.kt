package com.crichere.backend.auction

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/** Spring Data repository for [AuctionBidEntity]. */
interface AuctionBidRepository : JpaRepository<AuctionBidEntity, UUID> {

    /** The current leading bid for a player -- ignores [AuctionBidEntity.reversed] rows, so an `undo` naturally falls back to the next-highest one. */
    fun findTopByLeagueIdAndPlayerIdAndReversedFalseOrderByAmountDesc(leagueId: UUID, playerId: UUID): AuctionBidEntity?

    /** Also rejects a bid id that's real but belongs to a *different* league, same guard every other `findByIdAnd<Parent>` lookup in this codebase uses. */
    fun findByIdAndLeagueId(id: UUID, leagueId: UUID): Optional<AuctionBidEntity>
}
