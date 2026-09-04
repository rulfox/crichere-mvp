package com.crichere.backend.ground

import com.crichere.backend.ground.dto.GroundCreateRequest
import com.crichere.backend.ground.dto.GroundResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Search-and-register for shared, reusable grounds. `GET` is public (`permitAll()` in
 * `SecurityConfig`, matching leagues' own public-read posture); `POST` requires authentication
 * -- [userId] always comes from `@AuthenticationPrincipal`, never client input.
 */
@RestController
@RequestMapping("/api/v1/grounds")
class GroundController(
    private val groundService: GroundService,
) {

    /** Every filter optional -- an empty [search] with no other filters lists every ground. */
    @GetMapping
    fun search(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) district: String?,
        @RequestParam(required = false) city: String?,
    ): List<GroundResponse> = groundService.search(search, state, district, city)

    @PostMapping
    fun create(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: GroundCreateRequest,
    ): GroundResponse = groundService.create(userId, request)
}
