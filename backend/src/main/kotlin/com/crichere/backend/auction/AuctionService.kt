package com.crichere.backend.auction

import com.crichere.backend.auction.dto.AuctionBidTickerResponse
import com.crichere.backend.auction.dto.AuctionLastResultResponse
import com.crichere.backend.auction.dto.AuctionResultsResponse
import com.crichere.backend.auction.dto.AuctionStateResponse
import com.crichere.backend.auction.dto.FranchiseAuctionResultResponse
import com.crichere.backend.auction.dto.PlayerAuctionResultResponse
import com.crichere.backend.common.ContentRateLimitExceededException
import com.crichere.backend.common.ContentRateLimiter
import com.crichere.backend.franchise.FranchiseEntity
import com.crichere.backend.franchise.FranchiseRepository
import com.crichere.backend.franchise.LeagueFranchiseNotFoundException
import com.crichere.backend.franchise.NotFranchiseOwnerException
import com.crichere.backend.league.AuctionLastActionType
import com.crichere.backend.league.AuctionStatus
import com.crichere.backend.league.LeagueAuthorization
import com.crichere.backend.league.LeagueEntity
import com.crichere.backend.league.LeagueNotFoundException
import com.crichere.backend.league.LeagueRepository
import com.crichere.backend.notification.FcmSender
import com.crichere.backend.player.AuctionOutcome
import com.crichere.backend.player.LeaguePlayerNotFoundException
import com.crichere.backend.player.PlayerEntity
import com.crichere.backend.player.PlayerRepository
import com.crichere.backend.profile.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The live auction engine (see docs/PHASE5.md). One method per `AuctionController` endpoint, all
 * `@Transactional`. [placeBid] is the one method with a real concurrency requirement -- see its
 * own doc.
 */
@Service
class AuctionService(
    private val leagueRepository: LeagueRepository,
    private val playerRepository: PlayerRepository,
    private val franchiseRepository: FranchiseRepository,
    private val auctionBidRepository: AuctionBidRepository,
    private val profileRepository: ProfileRepository,
    private val contentRateLimiter: ContentRateLimiter,
    private val broadcastService: AuctionBroadcastService,
    private val leagueAuthorization: LeagueAuthorization,
    private val fcmSender: FcmSender,
) {

    /**
     * `POST /leagues/{id}/auction/start`. Organizer-only. Re-runs the readiness check
     * server-side -- settings fully configured, a non-empty pool, at least one active franchise,
     * and `auctionSquadMax * activeFranchiseCount` not exceeding the active pool size (see
     * docs/PHASE4.md's two-stage squad-math check -- this is the hard-block stage, against real
     * counts) -- rather than trusting a client already gated it.
     *
     * @throws com.crichere.backend.league.NotOrganizerException [callerId] is not this league's organizer.
     * @throws AuctionAlreadyStartedException the auction has already left `NOT_STARTED`.
     * @throws AuctionNotReadyException the readiness check fails.
     */
    @Transactional
    fun start(leagueId: UUID, callerId: UUID): AuctionStateResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        if (league.auctionStatus != AuctionStatus.NOT_STARTED) throw AuctionAlreadyStartedException()

        if (league.auctionBasePrice == null || league.auctionPurse == null ||
            league.auctionSquadMin == null || league.auctionSquadMax == null || league.auctionBidIncrement == null
        ) {
            throw AuctionNotReadyException("Auction settings are not fully configured.")
        }

        val activePlayers = playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId)
        if (activePlayers <= 0) throw AuctionNotReadyException("The auction pool is empty.")

        val activeFranchises = franchiseRepository.countByLeagueIdAndRemovedAtIsNull(leagueId)
        if (activeFranchises <= 0) throw AuctionNotReadyException("No franchise has claimed a spot in this league.")

        val squadMax = requireNotNull(league.auctionSquadMax)
        if (squadMax.toLong() * activeFranchises > activePlayers) {
            throw AuctionNotReadyException("Squad max would require more players than are in the pool.")
        }

        league.auctionStatus = AuctionStatus.IN_PROGRESS
        val response = saveAndBroadcast(league)
        notifyAuctionStarted(leagueId, league.name)
        return response
    }

    /** Push notification (docs/PHASE8.md) -- every active player and franchise owner in the league. */
    private fun notifyAuctionStarted(leagueId: UUID, leagueName: String) {
        val recipients = playerRepository.findByLeagueIdAndRemovedAtIsNull(leagueId).map { it.userId } +
            franchiseRepository.findByLeagueIdAndRemovedAtIsNull(leagueId).map { it.ownerUserId }
        recipients.distinct().forEach { userId ->
            fcmSender.sendToUser(userId, leagueName, "The auction is live -- come bid!", mapOf("leagueId" to leagueId.toString()))
        }
    }

    /**
     * `POST /leagues/{id}/auction/next-player`. Organizer-only. Picks uniformly at random among
     * still-`PENDING` players -- no persisted shuffle queue (see the implementation plan): the
     * same "random order, unsold comes back around" behavior falls out without extra state to
     * keep consistent. Auto-completes the auction once the pool is empty.
     *
     * @throws AuctionPlayerAlreadyOpenException a player is already open; close it with `sold`/`unsold` first.
     */
    @Transactional
    fun nextPlayer(leagueId: UUID, callerId: UUID): AuctionStateResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireInProgress(league)
        if (league.auctionCurrentPlayerId != null) throw AuctionPlayerAlreadyOpenException()

        clearLastAction(league)

        val pending = playerRepository.findByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING)
        if (pending.isEmpty()) {
            league.auctionStatus = AuctionStatus.COMPLETED
            return saveAndBroadcast(league)
        }

        val next = pending.random()
        league.auctionCurrentPlayerId = next.id
        league.auctionLotCounter += 1
        league.auctionCurrentBidAmount = null
        league.auctionCurrentLeadingFranchiseId = null
        return saveAndBroadcast(league)
    }

    /**
     * `POST /leagues/{id}/auction/bids`. Locks the league row for update ([LeagueRepository.findByIdForUpdate])
     * for the duration of this transaction so two bids arriving the same instant serialize
     * against each other rather than both reading the same stale current-bid value (see
     * docs/PHASE5.md's Security section). Every hard block below is re-validated here even though
     * a well-behaved client already checks them -- a direct API call must not be able to bypass
     * squad max, purse, or the minimum-bid rule.
     *
     * @throws com.crichere.backend.common.ContentRateLimitExceededException the caller has bid too many times recently.
     * @throws NotFranchiseOwnerException [callerId] does not own [franchiseId].
     * @throws AuctionNoPlayerOpenException no player is currently open.
     * @throws AlreadyLeadingException [franchiseId] already holds the leading bid -- a franchise cannot outbid itself.
     * @throws BidTooLowException [amount] is below the current bid plus increment (or base price if none yet).
     * @throws SquadFullException this bid would push the franchise's squad past `auctionSquadMax`.
     * @throws PurseExceededException this bid would exceed the franchise's remaining purse and exceeding it isn't allowed.
     */
    @Transactional
    fun placeBid(leagueId: UUID, franchiseId: UUID, callerId: UUID, amount: BigDecimal): AuctionStateResponse {
        contentRateLimiter.tryConsumeForBid(callerId)?.let { retryAfter ->
            throw ContentRateLimitExceededException(retryAfter)
        }

        val league = leagueRepository.findByIdForUpdate(leagueId) ?: throw LeagueNotFoundException()
        requireInProgress(league)

        val franchise = findFranchiseOrThrow(leagueId, franchiseId)
        if (franchise.ownerUserId != callerId) throw NotFranchiseOwnerException()

        val currentPlayerId = league.auctionCurrentPlayerId ?: throw AuctionNoPlayerOpenException()

        // Checked under the row lock, so two quick taps from the same owner can't both land.
        if (league.auctionCurrentLeadingFranchiseId == franchiseId) throw AlreadyLeadingException()

        val minimum = league.auctionCurrentBidAmount?.plus(requireNotNull(league.auctionBidIncrement))
            ?: requireNotNull(league.auctionBasePrice)
        if (amount < minimum) throw BidTooLowException(minimum)

        val wonPlayers = playerRepository.findBySoldToFranchiseId(franchiseId)
        val squadMax = requireNotNull(league.auctionSquadMax)
        if (wonPlayers.size + 1 > squadMax) throw SquadFullException()

        if (!league.auctionAllowExceedPurse) {
            val purse = requireNotNull(league.auctionPurse)
            val spent = wonPlayers.sumOf { it.soldPrice ?: BigDecimal.ZERO }
            if (spent + amount > purse) throw PurseExceededException()
        }

        val bid = auctionBidRepository.save(
            AuctionBidEntity(leagueId = leagueId, playerId = currentPlayerId, franchiseId = franchiseId, amount = amount),
        )

        league.auctionCurrentBidAmount = amount
        league.auctionCurrentLeadingFranchiseId = franchiseId
        league.auctionLastActionType = AuctionLastActionType.BID
        league.auctionLastActionBidId = bid.id
        league.auctionLastActionPlayerId = currentPlayerId
        return saveAndBroadcast(league)
    }

    /**
     * `POST /leagues/{id}/auction/sold`. Organizer-only, closes the current player to its leading
     * bidder. Auto-completes the auction if no `PENDING` player remains.
     *
     * @throws AuctionNoPlayerOpenException no player is currently open.
     * @throws NoBidsToSellException there is no leading bid -- use `unsold` instead.
     */
    @Transactional
    fun sold(leagueId: UUID, callerId: UUID): AuctionStateResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireInProgress(league)
        val playerId = league.auctionCurrentPlayerId ?: throw AuctionNoPlayerOpenException()
        val leadingFranchiseId = league.auctionCurrentLeadingFranchiseId ?: throw NoBidsToSellException()
        val bidAmount = requireNotNull(league.auctionCurrentBidAmount)

        val player = findPlayerOrThrow(leagueId, playerId)
        player.auctionOutcome = AuctionOutcome.SOLD
        player.soldToFranchiseId = leadingFranchiseId
        player.soldPrice = bidAmount
        playerRepository.save(player)

        league.auctionLastActionType = AuctionLastActionType.SOLD
        league.auctionLastActionPlayerId = playerId
        league.auctionLastActionBidId = null
        league.auctionCurrentPlayerId = null
        league.auctionCurrentBidAmount = null
        league.auctionCurrentLeadingFranchiseId = null
        completeIfPoolExhausted(league)
        val response = saveAndBroadcast(league)

        val franchiseName = franchiseRepository.findById(leadingFranchiseId).orElse(null)?.name ?: "a franchise"
        fcmSender.sendToUser(player.userId, league.name, "You were sold to $franchiseName for ₹$bidAmount", mapOf("leagueId" to leagueId.toString()))
        return response
    }

    /**
     * `POST /leagues/{id}/auction/unsold`. Organizer-only, closes the current player with no
     * sale. The player's `auction_outcome` was already `PENDING` (nobody bid) -- the only real
     * effect is clearing the current-player slot so it can be drawn again later (see
     * docs/PHASE5.md's Features: "Any player marked unsold is requeued for a later round").
     *
     * @throws AuctionNoPlayerOpenException no player is currently open.
     */
    @Transactional
    fun unsold(leagueId: UUID, callerId: UUID): AuctionStateResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireInProgress(league)
        val playerId = league.auctionCurrentPlayerId ?: throw AuctionNoPlayerOpenException()
        val player = findPlayerOrThrow(leagueId, playerId)

        league.auctionLastActionType = AuctionLastActionType.UNSOLD
        league.auctionLastActionPlayerId = playerId
        league.auctionLastActionBidId = null
        league.auctionCurrentPlayerId = null
        league.auctionCurrentBidAmount = null
        league.auctionCurrentLeadingFranchiseId = null
        val response = saveAndBroadcast(league)

        fcmSender.sendToUser(player.userId, league.name, "You went unsold -- you're back in the pool", mapOf("leagueId" to leagueId.toString()))
        return response
    }

    /**
     * `POST /leagues/{id}/auction/undo`. Organizer-only. Reverses exactly the last bid or the
     * last sold/unsold outcome (single-level -- see the implementation plan), then clears the
     * pointer so a fresh mistake is needed to make a new one undoable. Deliberately does not
     * require [AuctionStatus.IN_PROGRESS]: undoing the `sold` that auto-completed the auction must
     * still work, which is also why a successful `SOLD`/`UNSOLD` undo restores `IN_PROGRESS`.
     *
     * @throws NothingToUndoException there is no last action to reverse.
     */
    @Transactional
    fun undo(leagueId: UUID, callerId: UUID): AuctionStateResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)

        when (league.auctionLastActionType) {
            AuctionLastActionType.BID -> {
                val bidId = requireNotNull(league.auctionLastActionBidId)
                val bid = auctionBidRepository.findByIdAndLeagueId(bidId, leagueId).orElseThrow { NothingToUndoException() }
                bid.reversed = true
                auctionBidRepository.save(bid)

                val nextLeading = auctionBidRepository.findTopByLeagueIdAndPlayerIdAndReversedFalseOrderByAmountDesc(leagueId, bid.playerId)
                league.auctionCurrentBidAmount = nextLeading?.amount
                league.auctionCurrentLeadingFranchiseId = nextLeading?.franchiseId
            }

            AuctionLastActionType.SOLD -> {
                val playerId = requireNotNull(league.auctionLastActionPlayerId)
                val player = findPlayerOrThrow(leagueId, playerId)
                player.auctionOutcome = AuctionOutcome.PENDING
                player.soldToFranchiseId = null
                player.soldPrice = null
                playerRepository.save(player)

                league.auctionCurrentPlayerId = playerId
                restoreCurrentBidFrom(league, playerId)
                if (league.auctionStatus == AuctionStatus.COMPLETED) league.auctionStatus = AuctionStatus.IN_PROGRESS
            }

            AuctionLastActionType.UNSOLD -> {
                val playerId = requireNotNull(league.auctionLastActionPlayerId)
                league.auctionCurrentPlayerId = playerId
                restoreCurrentBidFrom(league, playerId)
            }

            null -> throw NothingToUndoException()
        }

        clearLastAction(league)
        return saveAndBroadcast(league)
    }

    /** `POST /leagues/{id}/auction/toggle-exceed-purse`. Organizer-only. No cap on how far over purse this allows -- see docs/PHASE5.md's Decisions Made. */
    @Transactional
    fun toggleExceedPurse(leagueId: UUID, callerId: UUID, allow: Boolean): AuctionStateResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireInProgress(league)
        league.auctionAllowExceedPurse = allow
        return saveAndBroadcast(league)
    }

    /**
     * `POST /leagues/{id}/auction/end`. Organizer-only, manually ends the auction early. Every
     * still-`PENDING` player (including whichever one is currently open, if any) is marked
     * `UNSOLD` -- a terminal state distinct from the re-queueing `unsold` action performs (see
     * [PlayerEntity]'s `AuctionOutcome` doc).
     */
    @Transactional
    fun end(leagueId: UUID, callerId: UUID): AuctionStateResponse {
        val league = findLeagueOrThrow(leagueId)
        leagueAuthorization.requireOrganizer(league, callerId)
        requireInProgress(league)

        playerRepository.findByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING).forEach { player ->
            player.auctionOutcome = AuctionOutcome.UNSOLD
            playerRepository.save(player)
        }

        league.auctionStatus = AuctionStatus.COMPLETED
        league.auctionCurrentPlayerId = null
        league.auctionCurrentBidAmount = null
        league.auctionCurrentLeadingFranchiseId = null
        clearLastAction(league)
        return saveAndBroadcast(league)
    }

    /** `GET /leagues/{id}/auction/results`. Public, no auth -- see docs/PHASE5.md's Features. */
    @Transactional(readOnly = true)
    fun results(leagueId: UUID): AuctionResultsResponse {
        val league = findLeagueOrThrow(leagueId)
        val franchises = franchiseRepository.findByLeagueIdAndRemovedAtIsNull(leagueId)

        val franchiseResults = franchises.map { franchise -> franchise.toResultResponse(league) }
        return AuctionResultsResponse(auctionStatus = league.auctionStatus, franchises = franchiseResults)
    }

    /** `GET /leagues/{id}/auction/stream`. Public SSE -- see `AuctionController`/[AuctionBroadcastService]. */
    @Transactional(readOnly = true)
    fun currentState(leagueId: UUID): AuctionStateResponse = findLeagueOrThrow(leagueId).toStateResponse()

    private fun restoreCurrentBidFrom(league: LeagueEntity, playerId: UUID) {
        val leading = auctionBidRepository.findTopByLeagueIdAndPlayerIdAndReversedFalseOrderByAmountDesc(league.id!!, playerId)
        league.auctionCurrentBidAmount = leading?.amount
        league.auctionCurrentLeadingFranchiseId = leading?.franchiseId
    }

    private fun completeIfPoolExhausted(league: LeagueEntity) {
        val stillPending = playerRepository.countByLeagueIdAndAuctionOutcome(requireNotNull(league.id), AuctionOutcome.PENDING)
        if (stillPending == 0L) league.auctionStatus = AuctionStatus.COMPLETED
    }

    private fun clearLastAction(league: LeagueEntity) {
        league.auctionLastActionType = null
        league.auctionLastActionBidId = null
        league.auctionLastActionPlayerId = null
    }

    private fun requireInProgress(league: LeagueEntity) {
        if (league.auctionStatus != AuctionStatus.IN_PROGRESS) throw AuctionNotInProgressException()
    }

    private fun saveAndBroadcast(league: LeagueEntity): AuctionStateResponse {
        league.updatedAt = Instant.now()
        val saved = leagueRepository.save(league)
        val state = saved.toStateResponse()
        broadcastService.broadcast(requireNotNull(saved.id), state)
        return state
    }

    private fun findLeagueOrThrow(leagueId: UUID): LeagueEntity =
        leagueRepository.findById(leagueId).orElseThrow { LeagueNotFoundException() }

    /** Also rejects a player id that's real but belongs to a *different* league. */
    private fun findPlayerOrThrow(leagueId: UUID, playerId: UUID): PlayerEntity =
        playerRepository.findByIdAndLeagueId(playerId, leagueId).orElseThrow { LeaguePlayerNotFoundException() }

    /** Also rejects a franchise id that's real but belongs to a *different* league -- see [FranchiseRepository]'s callers for the same guard elsewhere. */
    private fun findFranchiseOrThrow(leagueId: UUID, franchiseId: UUID): FranchiseEntity {
        val franchise = franchiseRepository.findById(franchiseId).orElseThrow { LeagueFranchiseNotFoundException() }
        if (franchise.leagueId != leagueId) throw LeagueFranchiseNotFoundException()
        return franchise
    }

    private fun LeagueEntity.toStateResponse(): AuctionStateResponse {
        val leagueId = requireNotNull(id)
        val currentPlayer = auctionCurrentPlayerId?.let { playerRepository.findById(it).orElse(null) }
        val currentProfile = currentPlayer?.let { profileRepository.findById(it.userId).orElse(null) }
        val leadingFranchise = auctionCurrentLeadingFranchiseId?.let { franchiseRepository.findById(it).orElse(null) }
        val recentBids = auctionCurrentPlayerId?.let { playerId ->
            auctionBidRepository.findTop8ByLeagueIdAndPlayerIdAndReversedFalseOrderByPlacedAtDesc(leagueId, playerId)
                .map { bid ->
                    AuctionBidTickerResponse(
                        franchiseId = bid.franchiseId,
                        franchiseName = franchiseRepository.findById(bid.franchiseId).orElse(null)?.name,
                        amount = bid.amount,
                        placedAt = bid.placedAt,
                    )
                }
        } ?: emptyList()
        val playersPending = playerRepository.countByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.PENDING).toInt()
        val blockers = biddingBlockers(playersPending)
        return AuctionStateResponse(
            auctionStatus = auctionStatus,
            currentPlayerId = auctionCurrentPlayerId,
            currentPlayerName = currentProfile?.name,
            currentBidAmount = auctionCurrentBidAmount,
            currentLeadingFranchiseId = auctionCurrentLeadingFranchiseId,
            currentLeadingFranchiseName = leadingFranchise?.name,
            allowExceedPurse = auctionAllowExceedPurse,
            recentBids = recentBids,
            currentPlayerPhotoUrl = currentProfile?.photoUrl,
            currentPlayerRole = currentProfile?.playingRole,
            playersTotal = playerRepository.countByLeagueIdAndRemovedAtIsNull(leagueId).toInt(),
            playersSold = playerRepository.countByLeagueIdAndAuctionOutcome(leagueId, AuctionOutcome.SOLD).toInt(),
            lastResult = if (auctionCurrentPlayerId == null) lastResult() else null,
            currentPlayerBattingStyle = currentProfile?.battingStyle,
            currentPlayerBowlingStyle = currentProfile?.bowlingStyle,
            currentLotNumber = auctionLotCounter.takeIf { it > 0 },
            playersPending = playersPending,
            canAnyoneBid = blockers.canAnyoneBid,
            franchisesTotal = blockers.franchisesTotal,
            squadsFull = blockers.squadsFull,
            purseBelowBase = blockers.purseBelowBase,
        )
    }

    /**
     * Whether anyone can bid, and why not. `canAnyoneBid` is `false` only when the auction is running,
     * players are still waiting, and no active franchise could place even an opening bid -- every squad is
     * full, or (with exceeding the purse off) every purse is below the base price. The counts split the
     * stopped franchises (squad full first, then purse) so the organizer's dead-end card can say which
     * switch helps (design update #4, A2). Players already won are read the same way [placeBid] reads
     * them, so the two cannot disagree. Counts stay zero unless nobody can bid.
     */
    private fun LeagueEntity.biddingBlockers(playersPending: Int): BiddingBlockers {
        if (auctionStatus != AuctionStatus.IN_PROGRESS || playersPending == 0) return BiddingBlockers()
        val squadMax = auctionSquadMax ?: return BiddingBlockers()
        val basePrice = auctionBasePrice ?: return BiddingBlockers()
        val purse = auctionPurse ?: return BiddingBlockers()
        val franchises = franchiseRepository.findByLeagueIdAndRemovedAtIsNull(requireNotNull(id))
        var squadsFull = 0
        var purseBelowBase = 0
        for (franchise in franchises) {
            val won = playerRepository.findBySoldToFranchiseId(requireNotNull(franchise.id))
            val spent = won.sumOf { it.soldPrice ?: BigDecimal.ZERO }
            when {
                won.size >= squadMax -> squadsFull++
                !auctionAllowExceedPurse && spent + basePrice > purse -> purseBelowBase++
                else -> return BiddingBlockers()
            }
        }
        return BiddingBlockers(canAnyoneBid = false, franchisesTotal = franchises.size, squadsFull = squadsFull, purseBelowBase = purseBelowBase)
    }

    private data class BiddingBlockers(
        val canAnyoneBid: Boolean = true,
        val franchisesTotal: Int = 0,
        val squadsFull: Int = 0,
        val purseBelowBase: Int = 0,
    )

    /** The player the organizer just sold or sent back unsold -- only while that's still the last action (undo/next player clear it). */
    private fun LeagueEntity.lastResult(): AuctionLastResultResponse? {
        val type = auctionLastActionType ?: return null
        if (type != AuctionLastActionType.SOLD && type != AuctionLastActionType.UNSOLD) return null
        val player = auctionLastActionPlayerId?.let { playerRepository.findById(it).orElse(null) } ?: return null
        val sold = type == AuctionLastActionType.SOLD
        return AuctionLastResultResponse(
            playerName = profileRepository.findById(player.userId).orElse(null)?.name,
            sold = sold,
            franchiseName = if (sold) player.soldToFranchiseId?.let { franchiseRepository.findById(it).orElse(null)?.name } else null,
            amount = if (sold) player.soldPrice else null,
        )
    }

    private fun FranchiseEntity.toResultResponse(league: LeagueEntity): FranchiseAuctionResultResponse {
        val won = playerRepository.findBySoldToFranchiseId(requireNotNull(id))
        val spent = won.sumOf { it.soldPrice ?: BigDecimal.ZERO }
        val squadMin = league.auctionSquadMin
        return FranchiseAuctionResultResponse(
            franchiseId = requireNotNull(id),
            franchiseName = name,
            playersWon = won.map { it.toResultResponse() },
            purseSpent = spent,
            purseRemaining = league.auctionPurse?.minus(spent),
            belowSquadMin = squadMin != null && won.size < squadMin,
        )
    }

    private fun PlayerEntity.toResultResponse(): PlayerAuctionResultResponse {
        val profile = profileRepository.findById(userId).orElse(null)
        return PlayerAuctionResultResponse(
            playerId = requireNotNull(id),
            userId = userId,
            playerName = profile?.name,
            soldPrice = requireNotNull(soldPrice),
            photoUrl = profile?.photoUrl,
            playingRole = profile?.playingRole,
        )
    }
}
