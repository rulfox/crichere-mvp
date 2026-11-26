package com.crichere.app.parity

import com.crichere.app.league.AuctionStatus
import com.crichere.app.league.LeagueAwardDto
import com.crichere.app.league.LeagueDto
import com.crichere.app.league.LeagueFranchiseDto
import com.crichere.app.league.LeaguePlayerDto
import com.crichere.app.league.LeagueRoleDto
import com.crichere.app.league.LeagueStatus
import com.crichere.app.profile.PlayingRole

/** The same fixture data as the iOS gallery (`iosApp/Design/Debug/Fixtures.swift`), so both apps render one board identically. */
object ParityFixtures {
    fun player(index: Int, name: String, role: PlayingRole? = PlayingRole.BATSMAN, screenshot: Boolean = false, leaveRequested: Boolean = false, deleted: Boolean = false) =
        LeaguePlayerDto(
            id = "p$index", userId = "u$index", name = name, joinedAt = "2026-09-26T10:00:00Z",
            paymentScreenshotUrl = if (screenshot) "https://fixtures.invalid/proof.jpg" else null,
            leaveRequestedAt = if (leaveRequested) "2026-10-01T10:00:00Z" else null, playingRole = role, deleted = deleted,
        )

    fun franchise(index: Int, name: String, owner: String, screenshot: Boolean = false) = LeagueFranchiseDto(
        id = "f$index", ownerUserId = "o$index", ownerName = owner, name = name, joinedAt = "2026-09-26T10:00:00Z",
        paymentScreenshotUrl = if (screenshot) "https://fixtures.invalid/proof.jpg" else null,
    )

    val players = listOf(
        player(1, "Aarav Pawar", PlayingRole.ALL_ROUNDER, screenshot = true),
        player(2, "Rohit Jadhav", PlayingRole.BATSMAN, screenshot = true),
        player(3, "Sahil Kulkarni", PlayingRole.BOWLER),
        player(4, "Vikram Patil", PlayingRole.WICKETKEEPER, screenshot = true),
        player(5, "Omkar Shinde", PlayingRole.BOWLER),
    )

    val franchises = listOf(
        franchise(1, "Kolhapur Kings", "Nikhil Desai", screenshot = true),
        franchise(2, "Sangli Strikers", "Pranav More"),
    )

    val awards = listOf(
        LeagueAwardDto("a1", "Winners", 25000.0, hasTrophy = true, displayOrder = 0),
        LeagueAwardDto("a2", "Runners-up", 11000.0, hasTrophy = true, displayOrder = 1),
        LeagueAwardDto("a3", "Player of the tournament", null, hasTrophy = true, displayOrder = 2),
    )

    fun league(
        id: String = "league-kpl",
        name: String = "Kolhapur Premier League",
        organizer: String = "u-organizer",
        organizerName: String? = "Sachin Gaikwad",
        ground: String = "Shahu Stadium",
        district: String = "Kolhapur",
        startsOn: String = "2026-10-12",
        format: String? = "T20",
        playersRequired: Int? = 40,
        franchisesRequired: Int? = 4,
        playerFee: Double? = 500.0,
        franchiseFee: Double? = 5000.0,
        upiId: String? = "sachin@okaxis",
        status: LeagueStatus = LeagueStatus.ANNOUNCED,
        players: List<LeaguePlayerDto> = ParityFixtures.players,
        franchises: List<LeagueFranchiseDto> = ParityFixtures.franchises,
        following: Boolean = false,
        logoRemoved: Boolean = false,
        banner: Boolean = false,
        awards: List<LeagueAwardDto> = emptyList(),
        auctionStatus: AuctionStatus? = null,
        handover: Boolean = false,
        coOrganizers: List<LeagueRoleDto> = emptyList(),
    ) = LeagueDto(
        id = id, organizerUserId = organizer, organizerName = organizerName, name = name,
        description = "Twelve-over league for Kolhapur district clubs.", bannerUrl = if (banner) "https://fixtures.invalid/banner.jpg" else null,
        logoRemoved = logoRemoved, state = "Maharashtra", district = district, groundId = "g1", groundName = ground, startsOn = startsOn,
        format = format, franchisesRequired = franchisesRequired, playersRequired = playersRequired, franchiseFee = franchiseFee,
        playerFee = playerFee, organizerUpiId = upiId, status = status, awards = awards, players = players, franchises = franchises,
        isFollowing = following, auctionBasePrice = 1000.0, auctionPurse = 100000.0, auctionSquadMin = 8, auctionSquadMax = 12,
        auctionBidIncrement = 500.0, coOrganizers = coOrganizers, auctionStatus = auctionStatus, showHandoverNotice = handover,
    )
}
