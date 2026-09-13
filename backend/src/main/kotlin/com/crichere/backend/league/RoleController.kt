package com.crichere.backend.league

import com.crichere.backend.league.dto.GrantRoleRequest
import com.crichere.backend.league.dto.LeagueResponse
import com.crichere.backend.league.dto.PhoneNumberLookupRequest
import com.crichere.backend.league.dto.RoleLookupResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Co-organizer role delegation (docs/PHASE7.md). Every endpoint here is organizer-scoped -- see [RoleService]. */
@RestController
@RequestMapping("/api/v1/leagues/{id}/roles")
class RoleController(
    private val roleService: RoleService,
) {

    @PostMapping("/lookup")
    fun lookup(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: PhoneNumberLookupRequest,
    ): RoleLookupResponse = roleService.lookup(id, userId, requireNotNull(request.phoneNumber))

    @PostMapping
    fun grant(
        @PathVariable id: UUID,
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: GrantRoleRequest,
    ): LeagueResponse = roleService.grant(id, userId, requireNotNull(request.userId))

    @DeleteMapping("/{roleId}")
    fun revoke(
        @PathVariable id: UUID,
        @PathVariable roleId: UUID,
        @AuthenticationPrincipal userId: UUID,
    ): LeagueResponse = roleService.revoke(id, userId, roleId)
}
