package com.crichere.backend.admin

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

data class PhoneLookupRequest(@field:NotBlank @field:Size(max = 20) val phone: String)

data class InternalFlagRequest(val internal: Boolean)

/** League and user browsers, phone lookup, internal flag and search (docs/PHASE20.md Step 9). */
@RestController
@RequestMapping("/api/v1/admin")
class AdminBrowseController(private val browse: AdminBrowseService) {

    @GetMapping("/leagues")
    fun leagues(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) district: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) createdFrom: LocalDate?,
        @RequestParam(required = false) createdTo: LocalDate?,
        @RequestParam(defaultValue = "false") hasOpenReports: Boolean,
        @RequestParam(defaultValue = "false") includeInternal: Boolean,
        @RequestParam(defaultValue = "newest") sort: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "25") limit: Int,
    ) = noStore(
        browse.leagues(
            LeagueFilters(q, state, district, status, createdFrom, createdTo, hasOpenReports, includeInternal, sort),
            cursor,
            limit.coerceIn(1, AdminBrowseService.MAX_PAGE),
        ),
    )

    @GetMapping("/leagues/{id}") fun leagueOverview(@PathVariable id: UUID) = noStore(browse.leagueOverview(id))

    @GetMapping("/leagues/{id}/franchises") fun leagueFranchises(@PathVariable id: UUID) = noStore(browse.leagueFranchises(id))

    @GetMapping("/leagues/{id}/players") fun leaguePlayers(@PathVariable id: UUID) = noStore(browse.leaguePlayers(id))

    @GetMapping("/leagues/{id}/auction") fun leagueAuction(@PathVariable id: UUID) = noStore(browse.leagueAuction(id))

    @GetMapping("/leagues/{id}/awards") fun leagueAwards(@PathVariable id: UUID) = noStore(browse.leagueAwards(id))

    @GetMapping("/leagues/{id}/moderation") fun leagueModeration(@PathVariable id: UUID) = noStore(browse.leagueModeration(id))

    @GetMapping("/leagues/{id}/timeline") fun leagueTimeline(@PathVariable id: UUID) = noStore(browse.leagueTimeline(id))

    /** A POST because it writes an audit row; the screenshot link it returns is short-lived. */
    @PostMapping("/payment-proofs/{kind}/{id}")
    fun paymentProof(@PathVariable kind: String, @PathVariable id: UUID, @AuthenticationPrincipal admin: AdminPrincipal) =
        noStore(browse.paymentProof(kind.uppercase(), id, admin.email))

    @GetMapping("/users")
    fun users(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) district: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) joinedFrom: LocalDate?,
        @RequestParam(required = false) joinedTo: LocalDate?,
        @RequestParam(defaultValue = "false") hasOpenReports: Boolean,
        @RequestParam(defaultValue = "false") includeInternal: Boolean,
        @RequestParam(defaultValue = "newest") sort: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "25") limit: Int,
    ) = noStore(
        browse.users(
            UserFilters(q, state, district, status, joinedFrom, joinedTo, hasOpenReports, includeInternal, sort),
            cursor,
            limit.coerceIn(1, AdminBrowseService.MAX_PAGE),
        ),
    )

    @GetMapping("/users/{id}") fun userProfile(@PathVariable id: UUID) = noStore(browse.userProfile(id))

    @GetMapping("/users/{id}/leagues") fun userLeagues(@PathVariable id: UUID) = noStore(browse.userLeagues(id))

    @GetMapping("/users/{id}/auction") fun userAuction(@PathVariable id: UUID) = noStore(browse.userAuction(id))

    @GetMapping("/users/{id}/activity") fun userActivity(@PathVariable id: UUID) = noStore(browse.userActivity(id))

    @GetMapping("/users/{id}/moderation") fun userModeration(@PathVariable id: UUID) = noStore(browse.userModeration(id))

    @GetMapping("/users/{id}/account") fun userAccount(@PathVariable id: UUID) = noStore(browse.userAccount(id))

    /** POST so the number never sits in a URL, a log line or browser history. */
    @PostMapping("/users/lookup")
    fun lookup(@Valid @RequestBody body: PhoneLookupRequest, @AuthenticationPrincipal admin: AdminPrincipal) =
        noStore(browse.lookupByPhone(body.phone, admin.email))

    @PutMapping("/users/{id}/internal")
    fun setInternal(@PathVariable id: UUID, @RequestBody body: InternalFlagRequest, @AuthenticationPrincipal admin: AdminPrincipal): ResponseEntity<Void> {
        browse.setInternal(id, body.internal, admin.email)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/search") fun search(@RequestParam q: String) = noStore(browse.search(q))

    private fun <T : Any> noStore(body: T): ResponseEntity<T> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)
}
