package com.crichere.backend.auction

import com.crichere.backend.auction.dto.LiveNowResponse
import com.crichere.backend.league.LeagueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Public "which auction is live right now" lookup for the landing page's "Watch live" links
 * (docs/PHASE11.md D5). Anonymous traffic can hit it freely, so the answer is memoized for
 * [TTL] -- a league going live shows up on the landing page within that window, which is fine
 * for a marketing link. Exposes nothing beyond what each league's own public page already does.
 */
@Service
class LiveNowService(
    private val leagueRepository: LeagueRepository,
    private val clock: Clock,
) {

    private data class Memo(val value: LiveNowResponse?, val expiresAt: Instant)

    private val memo = AtomicReference<Memo?>(null)

    @Transactional(readOnly = true)
    fun liveNow(): LiveNowResponse? {
        val now = clock.instant()
        memo.get()?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.value }
        val value = leagueRepository.findLiveNow()?.let { LiveNowResponse(leagueId = requireNotNull(it.id), leagueName = it.name) }
        memo.set(Memo(value, now.plus(TTL)))
        return value
    }

    companion object {
        val TTL: Duration = Duration.ofSeconds(15)
    }
}
