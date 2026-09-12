package com.crichere.backend.me

import com.crichere.backend.me.dto.MyLeaguesResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The current user's own cross-league standing -- see [MeService]. Authenticated (falls through to `.anyRequest().authenticated()`). */
@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val meService: MeService,
) {

    @GetMapping("/leagues")
    fun getMyLeagues(@AuthenticationPrincipal userId: UUID): MyLeaguesResponse = meService.getMyLeagues(userId)
}
