package com.crichere.backend.league

import com.crichere.backend.auction.AuctionAlreadyStartedException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Shared organizer-ownership check, injected into [LeagueService],
 * [com.crichere.backend.player.PlayerService], [com.crichere.backend.franchise.FranchiseService],
 * and [com.crichere.backend.auction.AuctionService] so the identical check isn't copy-pasted a
 * fourth and fifth time (see docs/PHASE3.md's implementation plan, decision 8).
 *
 * A `@Component`, not a pure free function, since Phase 7 (docs/PHASE7.md) widened "is this caller
 * allowed to act as this league's organizer" to also accept an active co-organizer grant, which
 * needs a repository lookup the old organizer-column-only comparison didn't.
 */
@Component
class LeagueAuthorization(private val leagueRoleRepository: LeagueRoleRepository) {

    /** @throws NotOrganizerException [callerId] is neither [league]'s organizer nor an active co-organizer. */
    fun requireOrganizer(league: LeagueEntity, callerId: UUID) {
        if (!isOrganizer(league, callerId)) throw NotOrganizerException()
    }

    /**
     * Non-throwing form, for the one call site ([com.crichere.backend.franchise.FranchiseService]'s
     * franchise-logo-upload check) that combines this with a second, unrelated permission.
     *
     * `||` short-circuits on [LeagueEntity.organizerUserId] first, so the real organizer's own
     * requests never touch `league_roles` at all -- the repository lookup only runs for a caller
     * who isn't the plain organizer, which is exactly the case Phase 7 actually added.
     */
    fun isOrganizer(league: LeagueEntity, callerId: UUID): Boolean =
        league.organizerUserId == callerId ||
            leagueRoleRepository.existsByLeagueIdAndUserIdAndRevokedAtIsNull(requireNotNull(league.id), callerId)
}

/**
 * Auction settings, joining/claiming, and roster removal all freeze the moment the auction leaves
 * `NOT_STARTED` (see docs/PHASE5.md's Decisions Made) -- shared by [LeagueService.updateAuctionSettings],
 * [com.crichere.backend.player.PlayerService], and [com.crichere.backend.franchise.FranchiseService]
 * so the rule lives in exactly one place. Stays a plain top-level function -- unlike organizer
 * identity, this has no notion of "who," so it needs no repository access.
 *
 * @throws AuctionAlreadyStartedException [league]'s auction has left `NOT_STARTED`.
 */
fun requireAuctionNotStarted(league: LeagueEntity) {
    if (league.auctionStatus != AuctionStatus.NOT_STARTED) throw AuctionAlreadyStartedException()
}
