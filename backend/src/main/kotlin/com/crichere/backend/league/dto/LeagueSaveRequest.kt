package com.crichere.backend.league.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Body of both `POST /api/v1/leagues` (create) and `PUT /api/v1/leagues/{id}` (edit) -- one
 * shape for both, since an edit is a full-replace of the same fields a create accepts (see
 * docs/PHASE2.md's Decisions Made on why `PUT` mirrors Phase 1's `PUT /profiles/me` full-replace
 * semantics). Unlike [com.crichere.backend.profile.dto.ProfileUpdateRequest], the required
 * fields below stay required on every save -- League Creation has no resumable partial-save, so
 * there's no "send what you have so far" case to support (confirmed during implementation, see
 * docs/PHASE2.md).
 */
data class LeagueSaveRequest(
    @field:NotBlank(message = "name is required")
    @field:Size(max = 200, message = "name must be at most 200 characters")
    val name: String,

    @field:Size(max = 4000, message = "description must be at most 4000 characters")
    val description: String? = null,

    // https only, like every other stored image URL: the web viewer fetches logoUrl server-side for
    // the share card, so an http:// or internal address here would be a request into our own
    // network (docs/SECURITY-AUDIT.md, below the bar).
    @field:Size(max = 2048, message = "logoUrl must be at most 2048 characters")
    @field:Pattern(regexp = "^https://.*", message = "logoUrl must be an https URL")
    val logoUrl: String? = null,

    @field:Size(max = 2048, message = "bannerUrl must be at most 2048 characters")
    @field:Pattern(regexp = "^https://.*", message = "bannerUrl must be an https URL")
    val bannerUrl: String? = null,

    @field:NotBlank(message = "state is required")
    val state: String,

    @field:NotBlank(message = "district is required")
    val district: String,

    @field:NotBlank(message = "city is required")
    val city: String,

    /** References an existing [com.crichere.backend.ground.GroundEntity] -- `null` means no ground attached yet. */
    val groundId: UUID? = null,

    @field:NotNull(message = "startsOn is required")
    val startsOn: LocalDate?,

    @field:Size(max = 100, message = "format must be at most 100 characters")
    val format: String? = null,

    @field:Positive(message = "franchisesRequired must be a positive number")
    val franchisesRequired: Int? = null,

    @field:Positive(message = "playersRequired must be a positive number")
    val playersRequired: Int? = null,

    @field:DecimalMin(value = "0.0", message = "franchiseFee cannot be negative")
    val franchiseFee: BigDecimal? = null,

    @field:DecimalMin(value = "0.0", message = "playerFee cannot be negative")
    val playerFee: BigDecimal? = null,

    /** Required (checked in `LeagueService`, not here -- the rule reads two sibling fields) once either fee above is set. See docs/PHASE3.md. */
    @field:Size(max = 255, message = "organizerUpiId must be at most 255 characters")
    val organizerUpiId: String? = null,

    /**
     * Optional initial awards, create-only (ignored by `PUT` -- once a league exists, awards are
     * managed through their own `POST`/`PUT`/`DELETE /api/v1/leagues/{id}/awards[/{awardId}]`
     * endpoints). Lets the mobile client's three pre-suggested rows ("First Prize" etc.) land in
     * the same call as the league itself, not three follow-up requests.
     */
    @field:Valid
    val awards: List<LeagueAwardSaveRequest>? = null,
)
