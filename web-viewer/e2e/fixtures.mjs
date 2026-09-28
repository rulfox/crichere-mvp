/**
 * Canned backend responses keyed by league id, served by `mock-server.mjs` and asserted against
 * by the specs. Plain JS (not `.ts`) so the standalone Node mock-server process can `import` it
 * directly with no transpile step -- Playwright's own test runner transpiles `.ts` specs, but a
 * plain `node e2e/mock-server.mjs` child process does not.
 */

const baseLeague = {
  id: "league-live",
  name: "Riverside Premier League",
  logoUrl: null,
  bannerUrl: null,
  country: "India",
  state: "Karnataka",
  district: "Bengaluru Urban",
  city: "Bengaluru",
  startsOn: "2026-10-04",
  format: "T20",
  players: [],
  franchises: [],
  auctionBasePrice: "2000",
  auctionPurse: "100000",
  auctionSquadMin: 11,
  auctionSquadMax: 15,
};

export const leagues = {
  "league-not-started": { ...baseLeague, id: "league-not-started", name: "Not Started League" },
  "league-live": { ...baseLeague, id: "league-live", name: "Live Auction League" },
  "league-completed": { ...baseLeague, id: "league-completed", name: "Completed League" },
};

export const auctionStates = {
  "league-not-started": {
    auctionStatus: "NOT_STARTED",
    currentPlayerId: null,
    currentPlayerName: null,
    currentBidAmount: null,
    currentLeadingFranchiseId: null,
    currentLeadingFranchiseName: null,
    allowExceedPurse: false,
    recentBids: [],
  },
  "league-live": {
    auctionStatus: "IN_PROGRESS",
    currentPlayerId: "player-1",
    currentPlayerName: "Rahul Sharma",
    currentBidAmount: "8000",
    currentLeadingFranchiseId: "franchise-1",
    currentLeadingFranchiseName: "Thunder Kings",
    allowExceedPurse: false,
    recentBids: [
      { franchiseId: "franchise-1", franchiseName: "Thunder Kings", amount: "8000", placedAt: "2026-09-28T10:00:00Z" },
      { franchiseId: "franchise-2", franchiseName: "Coastal Strikers", amount: "6000", placedAt: "2026-09-28T09:59:00Z" },
    ],
  },
  "league-completed": {
    auctionStatus: "COMPLETED",
    currentPlayerId: null,
    currentPlayerName: null,
    currentBidAmount: null,
    currentLeadingFranchiseId: null,
    currentLeadingFranchiseName: null,
    allowExceedPurse: false,
    recentBids: [],
  },
};

export const results = {
  "league-not-started": { auctionStatus: "NOT_STARTED", franchises: [] },
  "league-live": {
    auctionStatus: "IN_PROGRESS",
    franchises: [
      {
        franchiseId: "franchise-1",
        franchiseName: "Thunder Kings",
        playersWon: [{ playerId: "player-0", playerName: "Vikram Rao", soldPrice: "9000" }],
        purseSpent: "9000",
        purseRemaining: "91000",
        belowSquadMin: true,
      },
      {
        franchiseId: "franchise-2",
        franchiseName: "Coastal Strikers",
        playersWon: [],
        purseSpent: "0",
        purseRemaining: "100000",
        belowSquadMin: true,
      },
    ],
  },
  "league-completed": {
    auctionStatus: "COMPLETED",
    franchises: [
      {
        franchiseId: "franchise-1",
        franchiseName: "Thunder Kings",
        playersWon: [
          { playerId: "player-0", playerName: "Vikram Rao", soldPrice: "9000" },
          { playerId: "player-1", playerName: "Rahul Sharma", soldPrice: "8000" },
        ],
        purseSpent: "17000",
        purseRemaining: "83000",
        belowSquadMin: false,
      },
    ],
  },
};
