package com.crichere.app.parity

import androidx.compose.runtime.Composable
import com.crichere.app.auth.FakeAuthRepository
import com.crichere.app.league.AuctionBidTickerDto
import com.crichere.app.league.AuctionLastResultDto
import com.crichere.app.league.AuctionResultsDto
import com.crichere.app.league.AuctionState
import com.crichere.app.league.AuctionStateDto
import com.crichere.app.league.AuctionStatus
import com.crichere.app.league.AuctionViewModel
import com.crichere.app.league.ConnectionPhase
import com.crichere.app.league.EndAuctionFailure
import com.crichere.app.league.FakeAuctionRepository
import com.crichere.app.league.FakeLeagueRepository
import com.crichere.app.league.FranchiseAuctionResultDto
import com.crichere.app.league.PlayerAuctionResultDto
import com.crichere.app.profile.PlayingRole
import com.crichere.app.ui.AuctionLiveScreen

private val bids = listOf(
    AuctionBidTickerDto("f2", "Sangli Strikers", 15000.0, "2026-11-22T12:00:00Z"),
    AuctionBidTickerDto("f1", "Kolhapur Kings", 14500.0, "2026-11-22T11:59:40Z"),
    AuctionBidTickerDto("f2", "Sangli Strikers", 12000.0, "2026-11-22T11:59:00Z"),
)

private fun auctionDto(status: AuctionStatus = AuctionStatus.IN_PROGRESS, playerUp: Boolean = true, bid: Double? = 15000.0, leader: String? = "f2",
                       allow: Boolean = false, last: Boolean = false, squadsFull: Int = 0, purseBelow: Int = 0): AuctionStateDto {
    val leaderName = when (leader) { "f1" -> "Kolhapur Kings"; "f2" -> "Sangli Strikers"; else -> null }
    val bidUp = playerUp && bid != null
    return AuctionStateDto(
        auctionStatus = status, currentPlayerId = if (playerUp) "p1" else null, currentPlayerName = if (playerUp) "Aarav Pawar" else null,
        currentBidAmount = if (playerUp) bid else null, currentLeadingFranchiseId = if (bidUp) leader else null,
        currentLeadingFranchiseName = if (bidUp) leaderName else null, allowExceedPurse = allow, recentBids = if (bidUp) bids else emptyList(),
        currentPlayerRole = if (playerUp) PlayingRole.ALL_ROUNDER else null, playersTotal = 40, playersSold = 11,
        lastResult = if (last) AuctionLastResultDto("Rohit Jadhav", true, "Kolhapur Kings", 15000.0) else null,
        canAnyoneBid = squadsFull + purseBelow == 0, playersPending = 29, franchisesTotal = 2, squadsFull = squadsFull, purseBelowBase = purseBelow,
    )
}

private val finalResults = AuctionResultsDto(AuctionStatus.COMPLETED, listOf(
    FranchiseAuctionResultDto("f1", "Kolhapur Kings", listOf(
        PlayerAuctionResultDto("p1", "u1", "Aarav Pawar", 15000.0),
        PlayerAuctionResultDto("p2", "u2", "Rohit Jadhav", 52000.0),
        PlayerAuctionResultDto("p3", "u3", "Sahil Kulkarni", 41000.0, deleted = true),
    ), purseSpent = 108000.0, purseRemaining = -8000.0, belowSquadMin = true),
    FranchiseAuctionResultDto("f2", "Sangli Strikers", listOf(
        PlayerAuctionResultDto("p4", "u4", "Vikram Patil", 30000.0),
    ), purseSpent = 30000.0, purseRemaining = 70000.0, belowSquadMin = true),
))

private fun liveState(auction: AuctionStateDto?, organizer: Boolean = false, mine: String? = null, phase: ConnectionPhase = ConnectionPhase.ONLINE,
                      results: AuctionResultsDto? = null, input: String = "", bidding: Boolean = false, bidError: String? = null,
                      endOpen: Boolean = false, ending: Boolean = false, endFailure: EndAuctionFailure? = null, loadFailed: Boolean = false) = AuctionState(
    isLoading = auction == null && !loadFailed, loadFailed = loadFailed, connectionLost = phase == ConnectionPhase.LOST, connectionPhase = phase,
    lastEventAtMillis = System.currentTimeMillis() - 34_000, league = ParityFixtures.league(), isOrganizer = organizer, myFranchiseId = mine,
    auction = auction, results = results, bidAmountInput = input, isBidding = bidding, bidError = bidError, isEndConfirmOpen = endOpen,
    isEnding = ending, endFailure = endFailure,
)

private fun viewModel() = AuctionViewModel("league-kpl", FakeLeagueRepository(), FakeAuctionRepository(), FakeAuthRepository())

private fun live(state: AuctionState): @Composable () -> Unit = { AuctionLiveScreen(state = state, viewModel = viewModel(), onBack = {}) }

fun auctionBoards(): Map<String, @Composable () -> Unit> = mapOf(
    "L1" to live(liveState(auctionDto(), mine = "f1", input = "15500")),
    "L1-leading" to live(liveState(auctionDto(leader = "f1"), mine = "f1")),
    "L1-error" to live(liveState(auctionDto(), mine = "f1", input = "15100", bidError = "Bid at least ₹15,500.")),
    "L1-placing" to live(liveState(auctionDto(), mine = "f1", input = "15500", bidding = true)),
    "L2" to live(liveState(auctionDto(), organizer = true)),
    "L2-nobid" to live(liveState(auctionDto(bid = null), organizer = true)),
    "L3" to live(liveState(auctionDto(AuctionStatus.NOT_STARTED, playerUp = false), organizer = true)),
    "L3-viewer" to live(liveState(auctionDto(AuctionStatus.NOT_STARTED, playerUp = false))),
    "L4" to live(liveState(auctionDto())),
    "L5" to live(liveState(auctionDto(AuctionStatus.COMPLETED, playerUp = false), organizer = true, results = finalResults)),
    "L8" to live(liveState(auctionDto(playerUp = false, last = true), organizer = true)),
    "L10" to live(liveState(auctionDto(playerUp = false, last = true, squadsFull = 1, purseBelow = 1), organizer = true)),
    "L10-viewer" to live(liveState(auctionDto(playerUp = false, last = true, squadsFull = 1, purseBelow = 1))),
    "L16-reconnecting" to live(liveState(auctionDto(), mine = "f1", phase = ConnectionPhase.RECONNECTING)),
    "L16-lost" to live(liveState(auctionDto(), mine = "f1", phase = ConnectionPhase.LOST)),
    "L16-back" to live(liveState(auctionDto(), mine = "f1", phase = ConnectionPhase.BACK_ONLINE)),
    "L19" to live(liveState(auctionDto(), organizer = true, endOpen = true)),
    "L19-ending" to live(liveState(auctionDto(), organizer = true, endOpen = true, ending = true)),
    "L21" to live(liveState(auctionDto(playerUp = false), organizer = true, endFailure = EndAuctionFailure.NETWORK)),
    "L-loading" to live(liveState(null)),
    "L-error" to live(liveState(null, loadFailed = true)),
)
