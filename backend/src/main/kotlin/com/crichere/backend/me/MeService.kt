package com.crichere.backend.me

import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.ground.GroundRepository
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueFollowRepository
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.league.toSummaryResponse
import com.crichere.backend.me.dto.MyLeaguesResponse
import com.crichere.backend.notification.DeviceTokenEntity
import com.crichere.backend.notification.DeviceTokenRepository
import com.crichere.backend.player.PlayerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * `GET /api/v1/me/leagues` -- the four My Leagues lists (organizing/playing/franchiseOwner/
 * following) in one round trip (see docs/PHASE3.md's implementation plan, decision 4). Each
 * per-row league lookup is an accepted N+1, same tradeoff `LeagueService.toResponse` already
 * takes for awards; ground names for all rows come from one `findAllById`.
 */
@Service
class MeService(
    private val leagueRepository: LeagueRepository,
    private val playerRepository: PlayerRepository,
    private val franchiseRepository: FranchiseRepository,
    private val leagueFollowRepository: LeagueFollowRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val groundRepository: GroundRepository,
) {

    @Transactional(readOnly = true)
    fun getMyLeagues(callerId: UUID): MyLeaguesResponse {
        val organizing = leagueRepository.findByOrganizerUserId(callerId)
        val playing = playerRepository.findByUserIdAndRemovedAtIsNull(callerId)
            .mapNotNull { leagueRepository.findById(it.leagueId).orElse(null) }
        // distinctBy: unlike players (unique per league+user), a user can own more than one
        // franchise in the same league (see docs/PHASE3.md's Decisions Made -- "dual roles
        // allowed freely"), which would otherwise put the same league in this list twice. This
        // list represents leagues you own a franchise in, not one row per franchise.
        val franchiseOwner = franchiseRepository.findByOwnerUserIdAndRemovedAtIsNull(callerId)
            .mapNotNull { leagueRepository.findById(it.leagueId).orElse(null) }
            .distinctBy { it.id }
        val following = leagueFollowRepository.findByUserId(callerId)
            .mapNotNull { leagueRepository.findById(it.leagueId).orElse(null) }

        val groundNames = groundRepository
            .findAllById((organizing + playing + franchiseOwner + following).map { it.groundId }.toSet())
            .associate { requireNotNull(it.id) to it.name }
        fun List<LeagueEntity>.rows() = map { it.toSummaryResponse(groundNames.getValue(it.groundId)) }

        return MyLeaguesResponse(
            organizing = organizing.rows(),
            playing = playing.rows(),
            franchiseOwner = franchiseOwner.rows(),
            following = following.rows(),
        )
    }

    /**
     * `POST /me/device-tokens` (docs/PHASE8.md). Upserts by [token] alone, reassigning it onto
     * [callerId] regardless of who it belonged to before -- see that doc's Decisions Made on why
     * this is the correct behavior when a different account signs into the same device.
     */
    @Transactional
    fun registerDeviceToken(callerId: UUID, token: String, platform: String) {
        val existing = deviceTokenRepository.findByToken(token)
        if (existing != null) {
            existing.userId = callerId
            existing.platform = platform
            existing.updatedAt = Instant.now()
            deviceTokenRepository.save(existing)
        } else {
            deviceTokenRepository.save(DeviceTokenEntity(userId = callerId, token = token, platform = platform))
        }
    }

    /** `POST /me/device-tokens/unregister`. Scoped to the caller's own token -- see [DeviceTokenRepository.deleteByTokenAndUserId]. */
    @Transactional
    fun unregisterDeviceToken(callerId: UUID, token: String) {
        deviceTokenRepository.deleteByTokenAndUserId(token, callerId)
    }
}
