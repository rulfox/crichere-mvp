package com.crichere.backend.player

import com.crichere.backend.player.dto.LeaguePlayerJoinRequest
import com.crichere.backend.player.dto.LeaguePlayerResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Joining a league as a player, and the request-and-approve leave flow -- see [PlayerService]. Every endpoint here is authenticated (falls through to `.anyRequest().authenticated()` in `SecurityConfig` -- no new public-route line needed). */
@RestController
@RequestMapping("/api/v1/leagues/{leagueId}/players")
class PlayerController(
    private val playerService: PlayerService,
) {

    @PostMapping
    fun join(
        @PathVariable leagueId: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: LeaguePlayerJoinRequest,
    ): LeaguePlayerResponse = playerService.join(leagueId, userId, request)

    @DeleteMapping("/{playerId}")
    fun remove(
        @PathVariable leagueId: UUID,
        @PathVariable playerId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ) = playerService.remove(leagueId, playerId, userId)

    @PostMapping("/{playerId}/leave-request")
    fun requestLeave(
        @PathVariable leagueId: UUID,
        @PathVariable playerId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeaguePlayerResponse = playerService.requestLeave(leagueId, playerId, userId)

    @PostMapping("/{playerId}/leave-request/approve")
    fun approveLeave(
        @PathVariable leagueId: UUID,
        @PathVariable playerId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeaguePlayerResponse = playerService.approveLeave(leagueId, playerId, userId)

    @PostMapping("/{playerId}/leave-request/dismiss")
    fun dismissLeave(
        @PathVariable leagueId: UUID,
        @PathVariable playerId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeaguePlayerResponse = playerService.dismissLeave(leagueId, playerId, userId)
}
