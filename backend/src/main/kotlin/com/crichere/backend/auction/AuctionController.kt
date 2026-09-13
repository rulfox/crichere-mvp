package com.crichere.backend.auction

import com.crichere.backend.auction.dto.AuctionResultsResponse
import com.crichere.backend.auction.dto.AuctionStateResponse
import com.crichere.backend.auction.dto.PlaceBidRequest
import com.crichere.backend.auction.dto.ToggleExceedPurseRequest
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

/**
 * The live auction engine's endpoints (see docs/PHASE5.md). Every mutating endpoint is
 * organizer-only except `bids` (franchise-owner-only, checked in [AuctionService.placeBid]);
 * `stream` and `results` are public (`permitAll()` in `SecurityConfig`, matching this app's
 * public-by-default league posture) -- see [AuctionService] for the actual authorization checks.
 */
@RestController
@RequestMapping("/api/v1/leagues/{id}/auction")
class AuctionController(
    private val auctionService: AuctionService,
    private val broadcastService: AuctionBroadcastService,
) {

    @PostMapping("/start")
    fun start(@PathVariable id: UUID, @AuthenticationPrincipal userId: UUID): AuctionStateResponse =
        auctionService.start(id, userId)

    @PostMapping("/next-player")
    fun nextPlayer(@PathVariable id: UUID, @AuthenticationPrincipal userId: UUID): AuctionStateResponse =
        auctionService.nextPlayer(id, userId)

    @PostMapping("/bids")
    fun placeBid(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: PlaceBidRequest,
    ): AuctionStateResponse =
        auctionService.placeBid(id, requireNotNull(request.franchiseId), userId, requireNotNull(request.amount))

    @PostMapping("/sold")
    fun sold(@PathVariable id: UUID, @AuthenticationPrincipal userId: UUID): AuctionStateResponse =
        auctionService.sold(id, userId)

    @PostMapping("/unsold")
    fun unsold(@PathVariable id: UUID, @AuthenticationPrincipal userId: UUID): AuctionStateResponse =
        auctionService.unsold(id, userId)

    @PostMapping("/undo")
    fun undo(@PathVariable id: UUID, @AuthenticationPrincipal userId: UUID): AuctionStateResponse =
        auctionService.undo(id, userId)

    @PostMapping("/toggle-exceed-purse")
    fun toggleExceedPurse(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: ToggleExceedPurseRequest,
    ): AuctionStateResponse =
        auctionService.toggleExceedPurse(id, userId, requireNotNull(request.allow))

    @PostMapping("/end")
    fun end(@PathVariable id: UUID, @AuthenticationPrincipal userId: UUID): AuctionStateResponse =
        auctionService.end(id, userId)

    @GetMapping("/results")
    fun results(@PathVariable id: UUID): AuctionResultsResponse = auctionService.results(id)

    /** Public SSE stream -- see [AuctionBroadcastService]. */
    @GetMapping("/stream")
    fun stream(@PathVariable id: UUID): SseEmitter =
        broadcastService.subscribe(id, auctionService.currentState(id))
}
