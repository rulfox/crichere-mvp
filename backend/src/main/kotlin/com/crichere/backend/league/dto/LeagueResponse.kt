package com.crichere.backend.league.dto

import com.crichere.backend.franchise.dto.LeagueFranchiseResponse
import com.crichere.backend.league.LeagueStatus
import com.crichere.backend.player.dto.LeaguePlayerResponse
import java.math.BigDecimal
import java.time.Instant
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
    /** Only meaningful once a fee is set -- see docs/PHASE3.md. Public regardless of caller: a prospective joiner needs it before they've joined. */
    val organizerUpiId: String?,
    val status: LeagueStatus,
    val awards: List<LeagueAwardResponse>,
    val players: List<LeaguePlayerResponse>,
    val franchises: List<LeagueFranchiseResponse>,
    /** `false` for an anonymous caller. No follower list is embedded -- nothing browses one, see docs/PHASE3.md's Screens section. */
    val isFollowing: Boolean,
    /** All nullable until the organizer configures auction settings -- see docs/PHASE4.md. Public on read, same posture as capacity/fees -- the auction pool itself is just [players], purse is the same [auctionPurse] number for every franchise. */
    val auctionBasePrice: BigDecimal?,
    val auctionPurse: BigDecimal?,
    val auctionSquadMin: Int?,
    val auctionSquadMax: Int?,
    val auctionBidIncrement: BigDecimal?,
    /** `true` when `auctionSquadMax * franchisesRequired > playersRequired` (both present) -- a save-time-only warning, never a rejection (see docs/PHASE4.md's two-stage squad-math check; the hard-block re-check against real counts is Phase 5's). */
    val auctionSquadMaxWarning: Boolean,
    /** Active co-organizer grants (see docs/PHASE7.md) -- public, same posture as [organizerUserId] and every franchise owner's name already on this response. */
    val coOrganizers: List<LeagueRoleResponse>,
    /** Optional "bidding opens at" time, set with the auction settings (docs/PHASE11.md D3). */
    val auctionScheduledAt: Instant? = null,
)
