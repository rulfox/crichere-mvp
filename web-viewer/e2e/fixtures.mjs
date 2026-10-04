/**
 * Canned backend responses keyed by league id, served by `mock-server.mjs` and asserted against
 * by the specs. Plain JS (not `.ts`) so the standalone Node mock-server process can `import` it
 * directly with no transpile step -- Playwright's own test runner transpiles `.ts` specs, but a
 * plain `node e2e/mock-server.mjs` child process does not.
 *
 * The data mirrors the design handoff's sample league (docs/PHASE11.md) so the rendered pages can
 * be compared against the design references directly.
 */

const FRANCHISE_NAMES = ["Spartanz", "Victory CC", "Ashes Komalapuram", "Mannancherry United", "Rising Stars", "UK Kings"];
const franchises = FRANCHISE_NAMES.map((name, i) => ({ id: `franchise-${i + 1}`, name, logoUrl: null }));
const players = Array.from({ length: 30 }, (_, i) => ({ id: `player-${i + 1}`, name: `Player ${i + 1}` }));

const baseLeague = {
  id: "league-live",
  name: "Spartanz Premier League",
  logoUrl: null,
  bannerUrl: null,
  country: "India",
  state: "Kerala",
  district: "Alappuzha",
  city: "Pathirappally",
  groundName: "Udhaya Ground, Pathirappally",
  startsOn: "2026-10-16",
  format: "T10",
  franchisesRequired: 6,
  players,
  franchises,
  auctionBasePrice: "20000",
  auctionPurse: "500000",
  auctionSquadMin: 4,
  auctionSquadMax: 6,
  auctionScheduledAt: null,
};

export const leagues = {
  "league-not-started": { ...baseLeague, id: "league-not-started", name: "Not Started League", auctionScheduledAt: "2026-10-12T13:30:00Z" },
  "league-live": { ...baseLeague, id: "league-live", name: "Live Auction League" },
  "league-sold": { ...baseLeague, id: "league-sold", name: "Sold Moment League" },
  "league-unsold": { ...baseLeague, id: "league-unsold", name: "Unsold Moment League" },
  "league-flaky": { ...baseLeague, id: "league-flaky", name: "Flaky Connection League" },
  "league-completed": { ...baseLeague, id: "league-completed", name: "Completed League" },
  // Share-card (opengraph-image) states, docs/PHASE13.md -- no auction stream, only metadata + image.
  "league-share-logo": {
    ...baseLeague,
    id: "league-share-logo",
    name: "Kochi Super Sixes",
    city: "Kochi",
    state: "Kerala",
    description: "Six teams, one auction night.",
    logoUrl: "http://localhost:4310/mock-assets/logo.png",
  },
  "league-share-svg-logo": {
    ...baseLeague,
    id: "league-share-svg-logo",
    name: "Thar Strikers",
    city: "Jodhpur",
    state: "Rajasthan",
    logoUrl: "http://localhost:4310/mock-assets/logo.svg",
  },
  "league-share-long": {
    ...baseLeague,
    id: "league-share-long",
    name: "Sahyadri Amateur Inter-Society Tennis-Ball Cricket Premier League and Auction Night Series 2027",
    city: "Thane",
    state: "Maharashtra",
    auctionScheduledAt: "2099-01-02T12:30:00Z",
  },
};

const idle = {
  currentPlayerId: null,
  currentPlayerName: null,
  currentBidAmount: null,
  currentLeadingFranchiseId: null,
  currentLeadingFranchiseName: null,
  allowExceedPurse: false,
  recentBids: [],
  playersTotal: 30,
  playersSold: 0,
  playersPending: 30,
  currentLotNumber: null,
  lastResult: null,
};

const bid = (franchise, amount, placedAt) => ({ franchiseId: `franchise-${franchise}`, franchiseName: FRANCHISE_NAMES[franchise - 1], amount, placedAt });

/** Aswin Sudarsanan on the block, Victory CC leading at ₹1,20,000. */
const openLot = {
  ...idle,
  auctionStatus: "IN_PROGRESS",
  currentPlayerId: "player-1",
  currentPlayerName: "Aswin Sudarsanan",
  currentPlayerPhotoUrl: null,
  currentPlayerRole: "ALL_ROUNDER",
  currentPlayerBattingStyle: "RIGHT_HAND",
  currentPlayerBowlingStyle: "RIGHT_ARM_MEDIUM",
  currentLotNumber: 12,
  playersSold: 11,
  playersPending: 19,
  currentBidAmount: "120000",
  currentLeadingFranchiseId: "franchise-2",
  currentLeadingFranchiseName: "Victory CC",
  recentBids: [
    bid(2, "120000", "2026-09-28T10:00:30Z"),
    bid(1, "100000", "2026-09-28T10:00:20Z"),
    bid(2, "80000", "2026-09-28T10:00:10Z"),
    bid(3, "60000", "2026-09-28T10:00:00Z"),
  ],
};

/** An open lot nobody has bid on (closes UNSOLD in the scripted stream). */
const quietLot = {
  ...openLot,
  currentPlayerId: "player-3",
  currentPlayerName: "Sreeraj",
  currentPlayerRole: "BOWLER",
  currentPlayerBattingStyle: "RIGHT_HAND",
  currentPlayerBowlingStyle: "LEFT_ARM_ORTHODOX",
  currentLotNumber: 14,
  currentBidAmount: null,
  currentLeadingFranchiseId: null,
  currentLeadingFranchiseName: null,
  recentBids: [],
};

/**
 * What each league's SSE stream sends: a list of `{ afterMs, state }` events, sent in order after
 * the given delay from connecting. `league-flaky` additionally drops the connection after its
 * first event and refuses every reconnect (see mock-server.mjs), to show the reconnecting state.
 */
export const auctionStreams = {
  "league-not-started": [{ afterMs: 0, state: { ...idle, auctionStatus: "NOT_STARTED" } }],
  "league-live": [{ afterMs: 0, state: openLot }],
  "league-sold": [
    { afterMs: 0, state: openLot },
    {
      afterMs: 1200,
      state: {
        ...idle,
        auctionStatus: "IN_PROGRESS",
        currentLotNumber: 12,
        playersSold: 12,
        playersPending: 18,
        lastResult: { playerName: "Aswin Sudarsanan", sold: true, franchiseName: "Victory CC", amount: "120000" },
      },
    },
  ],
  "league-unsold": [
    { afterMs: 0, state: quietLot },
    {
      afterMs: 1200,
      state: {
        ...idle,
        auctionStatus: "IN_PROGRESS",
        currentLotNumber: 14,
        playersSold: 11,
        playersPending: 19,
        lastResult: { playerName: "Sreeraj", sold: false, franchiseName: null, amount: null },
      },
    },
  ],
  "league-flaky": [{ afterMs: 0, state: openLot }],
  "league-completed": [{ afterMs: 0, state: { ...idle, auctionStatus: "COMPLETED", playersSold: 24, playersPending: 0 } }],
};

const won = (id, name, price, role) => ({ playerId: id, playerName: name, soldPrice: price, playingRole: role });

const liveStandings = [
  { franchiseId: "franchise-1", franchiseName: "Spartanz", playersWon: [won("p-a", "Syam", "90000", "BATSMAN"), won("p-b", "Sarath", "100000", "BOWLER")], purseSpent: "190000", purseRemaining: "310000", belowSquadMin: true },
  { franchiseId: "franchise-2", franchiseName: "Victory CC", playersWon: [won("p-c", "Tony Jose", "140000", "WICKETKEEPER")], purseSpent: "140000", purseRemaining: "360000", belowSquadMin: true },
  { franchiseId: "franchise-3", franchiseName: "Ashes Komalapuram", playersWon: [won("p-d", "Reni", "40000", "ALL_ROUNDER"), won("p-e", "Arun", "40000", "BATSMAN")], purseSpent: "80000", purseRemaining: "420000", belowSquadMin: true },
  { franchiseId: "franchise-4", franchiseName: "Mannancherry United", playersWon: [won("p-f", "Jithu", "110000", "BOWLER"), won("p-g", "Appu", "100000", "BATSMAN"), won("p-h", "Bharath", "100000", "ALL_ROUNDER")], purseSpent: "310000", purseRemaining: "190000", belowSquadMin: true },
  { franchiseId: "franchise-5", franchiseName: "Rising Stars", playersWon: [won("p-i", "Bony", "60000", "BOWLER")], purseSpent: "60000", purseRemaining: "440000", belowSquadMin: true },
  { franchiseId: "franchise-6", franchiseName: "UK Kings", playersWon: [won("p-j", "Dileep", "120000", "BATSMAN"), won("p-k", "Jino", "425000", "WICKETKEEPER")], purseSpent: "545000", purseRemaining: "-45000", belowSquadMin: true }, // over purse (U5 W5)
];

/** After the scripted SOLD: Aswin Sudarsanan joins Victory CC. */
const soldStandings = liveStandings.map((f) =>
  f.franchiseId === "franchise-2"
    ? { ...f, playersWon: [...f.playersWon, won("player-1", "Aswin Sudarsanan", "120000", "ALL_ROUNDER")], purseSpent: "260000", purseRemaining: "240000" }
    : f,
);

const completedFranchises = [
  { franchiseId: "franchise-1", franchiseName: "Spartanz", playersWon: [won("c1", "Aswin Sudarsanan", "150000", "AR"), won("c2", "Syam", "65000", "BATSMAN"), won("c3", "Sarath", "40000", "BOWLER"), won("c4", "Tony Jose", "35000", "WICKETKEEPER"), won("c5", "Reni", "20000", "BATSMAN")], purseSpent: "310000", purseRemaining: "190000", belowSquadMin: false },
  { franchiseId: "franchise-2", franchiseName: "Victory CC", playersWon: [won("c6", "Deepak Boche", "120000", "BOWLER"), won("c7", "Akhil Joseph", "55000", "ALL_ROUNDER"), won("c8", "Arun", "45000", "BATSMAN"), won("c9", "Jithu", "305000", "WICKETKEEPER")], purseSpent: "525000", purseRemaining: "-25000", belowSquadMin: false }, // over purse (U5 W6)
  { franchiseId: "franchise-5", franchiseName: "Rising Stars", playersWon: [won("c10", "Rahul Sharma", "110000", "BATSMAN"), won("c11", "Unni", "30000", "BOWLER"), won("c12", "Suman", "20000", "ALL_ROUNDER")], purseSpent: "160000", purseRemaining: "340000", belowSquadMin: true },
].map((f) => ({ ...f, playersWon: f.playersWon.map((p) => ({ ...p, playingRole: ["BATSMAN", "BOWLER", "ALL_ROUNDER", "WICKETKEEPER"].includes(p.playingRole) ? p.playingRole : "ALL_ROUNDER" })) }));

export const results = {
  "league-not-started": { auctionStatus: "NOT_STARTED", franchises: [] },
  "league-live": { auctionStatus: "IN_PROGRESS", franchises: liveStandings },
  "league-sold": { auctionStatus: "IN_PROGRESS", franchises: soldStandings },
  "league-unsold": { auctionStatus: "IN_PROGRESS", franchises: liveStandings },
  "league-flaky": { auctionStatus: "IN_PROGRESS", franchises: liveStandings },
  "league-completed": { auctionStatus: "COMPLETED", franchises: completedFranchises },
};
