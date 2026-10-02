package com.crichere.backend.auction

import com.crichere.backend.auction.dto.LiveNowResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Public, `permitAll()` in `SecurityConfig` -- see [LiveNowService]. `204` when no auction is in progress. */
@RestController
class LiveNowController(private val liveNowService: LiveNowService) {

    @GetMapping("/api/v1/auctions/live-now")
    fun liveNow(): ResponseEntity<LiveNowResponse> =
        liveNowService.liveNow()?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()
}
