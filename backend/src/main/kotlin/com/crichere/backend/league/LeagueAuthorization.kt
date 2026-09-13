package com.crichere.backend.league

import com.crichere.backend.auction.AuctionAlreadyStartedException
import java.util.UUID

/**
 * Shared organizer-ownership check, promoted out of [LeagueService]'s own former private method
 * so [com.crichere.backend.player.PlayerService] and [com.crichere.backend.franchise.FranchiseService]
 * can reuse the identical check without copy-pasting it a second and third time (see
 * docs/PHASE3.md's implementation plan, decision 8).
 *
 * @throws NotOrganizerException [callerId] is not [league]'s organizer.
 */
fun requireOrganizer(league: LeagueEntity, callerId: UUID) {
    if (league.organizerUserId != callerId) throw NotOrganizerException()
}

/**
 * Auction settings, joining/claiming, and roster removal all freeze the moment the auction leaves
 * `NOT_STARTED` (see docs/PHASE5.md's Decisions Made) -- shared by [LeagueService.updateAuctionSettings],
 * [com.crichere.backend.player.PlayerService], and [com.crichere.backend.franchise.FranchiseService]
 * so the rule lives in exactly one place.
 *
 * @throws AuctionAlreadyStartedException [league]'s auction has left `NOT_STARTED`.
 */
fun requireAuctionNotStarted(league: LeagueEntity) {
    if (league.auctionStatus != AuctionStatus.NOT_STARTED) throw AuctionAlreadyStartedException()
}
