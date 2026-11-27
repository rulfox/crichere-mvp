#if DEBUG
import Shared

/// Fixture data for the DEBUG parity gallery: the shared DTOs with sensible defaults, so a board state
/// only spells out what makes it different. Kotlin constructors take every field from Swift.
enum Fixtures {
    static func player(_ index: Int, name: String, role: PlayingRole? = .batsman, screenshot: Bool = false,
                       leaveRequested: Bool = false, deleted: Bool = false) -> LeaguePlayerDto {
        LeaguePlayerDto(id: "p\(index)", userId: "u\(index)", name: name, joinedAt: "2026-09-26T10:00:00Z",
                        paymentScreenshotUrl: screenshot ? "https://fixtures.invalid/proof.jpg" : nil,
                        leaveRequestedAt: leaveRequested ? "2026-10-01T10:00:00Z" : nil, playingRole: role, deleted: deleted,
                        photoUrl: nil, photoRemoved: false)
    }

    static func franchise(_ index: Int, name: String, owner: String, screenshot: Bool = false) -> LeagueFranchiseDto {
        LeagueFranchiseDto(id: "f\(index)", ownerUserId: "o\(index)", ownerName: owner, name: name, logoUrl: nil,
                           joinedAt: "2026-09-26T10:00:00Z", paymentScreenshotUrl: screenshot ? "https://fixtures.invalid/proof.jpg" : nil,
                           leaveRequestedAt: nil, ownerDeleted: false, logoRemoved: false)
    }

    static let players: [LeaguePlayerDto] = [
        player(1, name: "Aarav Pawar", role: .allRounder, screenshot: true),
        player(2, name: "Rohit Jadhav", role: .batsman, screenshot: true),
        player(3, name: "Sahil Kulkarni", role: .bowler),
        player(4, name: "Vikram Patil", role: .wicketkeeper, screenshot: true),
        player(5, name: "Omkar Shinde", role: .bowler),
    ]

    static let franchises: [LeagueFranchiseDto] = [
        franchise(1, name: "Kolhapur Kings", owner: "Nikhil Desai", screenshot: true),
        franchise(2, name: "Sangli Strikers", owner: "Pranav More"),
    ]

    static func league(
        id: String = "league-kpl",
        name: String = "Kolhapur Premier League",
        organizer: String = "u-organizer",
        organizerName: String? = "Sachin Gaikwad",
        ground: String = "Shahu Stadium",
        district: String = "Kolhapur",
        startsOn: String = "2026-10-12",
        format: String? = "T20",
        playersRequired: Int32? = 40,
        franchisesRequired: Int32? = 4,
        playerFee: Double? = 500,
        franchiseFee: Double? = 5000,
        upiId: String? = "sachin@okaxis",
        status: LeagueStatus = .announced,
        players: [LeaguePlayerDto] = Fixtures.players,
        franchises: [LeagueFranchiseDto] = Fixtures.franchises,
        following: Bool = false,
        logoRemoved: Bool = false,
        banner: Bool = false,
        awards: [LeagueAwardDto] = [],
        auctionStatus: AuctionStatus? = nil,
        handover: Bool = false,
        coOrganizers: [LeagueRoleDto] = []
    ) -> LeagueDto {
        LeagueDto(
            id: id, organizerUserId: organizer, organizerName: organizerName, organizerDeleted: false, name: name,
            description: "Twelve-over league for Kolhapur district clubs.", logoUrl: nil,
            bannerUrl: banner ? "https://fixtures.invalid/banner.jpg" : nil, logoRemoved: logoRemoved,
            bannerRemoved: false, country: "IN", state: "Maharashtra", district: district, groundId: "g1", groundName: ground,
            startsOn: startsOn, format: format, franchisesRequired: franchisesRequired.map { KotlinInt(value: $0) },
            playersRequired: playersRequired.map { KotlinInt(value: $0) }, franchiseFee: franchiseFee.map { KotlinDouble(value: $0) },
            playerFee: playerFee.map { KotlinDouble(value: $0) }, organizerUpiId: upiId, status: status, awards: awards,
            players: players, franchises: franchises, isFollowing: following, auctionBasePrice: KotlinDouble(value: 1000),
            auctionPurse: KotlinDouble(value: 100000), auctionSquadMin: KotlinInt(value: 8), auctionSquadMax: KotlinInt(value: 12),
            auctionBidIncrement: KotlinDouble(value: 500), auctionBidRanges: [], auctionSquadMaxWarning: false, coOrganizers: coOrganizers, auctionScheduledAt: nil,
            auctionStatus: auctionStatus, organizerHandoverAt: nil, showHandoverNotice: handover)
    }

    static let awards: [LeagueAwardDto] = [
        LeagueAwardDto(id: "a1", name: "Winners", cashAmount: KotlinDouble(value: 25000), hasTrophy: true, displayOrder: 0),
        LeagueAwardDto(id: "a2", name: "Runners-up", cashAmount: KotlinDouble(value: 11000), hasTrophy: true, displayOrder: 1),
        LeagueAwardDto(id: "a3", name: "Player of the tournament", cashAmount: nil, hasTrophy: true, displayOrder: 2),
    ]

    /// The dashboard list: varied names (tile colours), formats, fees and fill levels.
    static let dashboardLeagues: [LeagueDto] = [
        league(),
        league(id: "league-scl", name: "Sangli Champions League", ground: "Willingdon College Ground", district: "Sangli",
               startsOn: "2026-10-19", format: "T10", playersRequired: 24, playerFee: nil, players: Array(players.prefix(3))),
        league(id: "league-ptc", name: "Pune Tennis Cricket Cup 2026", ground: "PYC Gymkhana", district: "Pune", startsOn: "2026-11-02",
               format: nil, playersRequired: nil, playerFee: 1500, players: Array(players.prefix(2))),
        league(id: "league-sbt", name: "Satara Box Turf Bash", ground: "Shivaji Turf", district: "Satara", startsOn: "2026-11-09",
               format: "Box cricket", playersRequired: 12, playerFee: 300, players: Array(players.prefix(5))),
    ]
}
#endif
