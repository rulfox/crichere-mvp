package com.crichere.backend.league.dto

import com.crichere.backend.league.LeagueStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Body of `GET /api/v1/leagues/{id}`, one row of `GET /api/v1/leagues`, and the response of create/edit/complete. */
data class LeagueResponse(
    val id: UUID,
    val organizerUserId: UUID,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val bannerUrl: String?,
    val country: String,
    val state: String,
    val district: String,
    val city: String,
    val groundId: UUID?,
    /** Null whenever [groundId] is null; also null (not an error) if the referenced ground was
     * somehow deleted after this league linked to it -- Phase 2 has no ground-delete feature, so
     * that case doesn't arise in practice, but the field shouldn't crash the response if it did. */
    val groundName: String?,
    val startsOn: LocalDate,
    val format: String?,
    val franchisesRequired: Int?,
    val playersRequired: Int?,
    val franchiseFee: BigDecimal?,
    val playerFee: BigDecimal?,
    val status: LeagueStatus,
    val awards: List<LeagueAwardResponse>,
)
