package com.crichere.backend.me

import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.league.LeagueFollowRepository
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.toSummaryResponse
import com.crichere.backend.me.dto.MyLeaguesResponse
import com.crichere.backend.player.PlayerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * `GET /api/v1/me/leagues` -- the four My Leagues lists (organizing/playing/franchiseOwner/
 * following) in one round trip (see docs/PHASE3.md's implementation plan, decision 4). Each
 * per-row league lookup is an accepted N+1, same tradeoff `LeagueService.toResponse` already
 * takes for ground/awards.
 */
@Service
class MeService(
    private val leagueRepository: LeagueRepository,
    private val playerRepository: PlayerRepository,
    private val franchiseRepository: FranchiseRepository,
    private val leagueFollowRepository: LeagueFollowRepository,
) {

    @Transactional(readOnly = true)
    fun getMyLeagues(callerId: UUID): MyLeaguesResponse {
        val organizing = leagueRepository.findByOrganizerUserId(callerId)
        val playing = playerRepository.findByUserIdAndRemovedAtIsNull(callerId)
            .mapNotNull { leagueRepository.findById(it.leagueId).orElse(null) }
        val franchiseOwner = franchiseRepository.findByOwnerUserIdAndRemovedAtIsNull(callerId)
            .mapNotNull { leagueRepository.findById(it.leagueId).orElse(null) }
        val following = leagueFollowRepository.findByUserId(callerId)
            .mapNotNull { leagueRepository.findById(it.leagueId).orElse(null) }

        return MyLeaguesResponse(
            organizing = organizing.map { it.toSummaryResponse() },
            playing = playing.map { it.toSummaryResponse() },
            franchiseOwner = franchiseOwner.map { it.toSummaryResponse() },
            following = following.map { it.toSummaryResponse() },
        )
    }
}
