package com.crichere.backend.league

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
