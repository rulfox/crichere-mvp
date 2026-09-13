package com.crichere.backend.league.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

// [LeagueRole] has exactly one value (CO_ORGANIZER) this phase, so the grant request doesn't ask
// which role -- there's nothing to choose between yet. A `role` field belongs here the day a
// second role actually ships, not before (see docs/PHASE7.md's Decisions Made on the `role`
// column existing specifically to avoid a schema change then).

/** Body of `POST /leagues/{id}/roles/lookup`. */
data class PhoneNumberLookupRequest(
    @field:NotBlank
    val phoneNumber: String?,
)

/** Response of a successful lookup -- not yet a grant, just "here's who that number belongs to." */
data class RoleLookupResponse(
    val userId: UUID,
    val name: String?,
)

/** Body of `POST /leagues/{id}/roles`. Always grants `CO_ORGANIZER` -- see the note above. */
data class GrantRoleRequest(
    @field:NotNull
    val userId: UUID?,
)
