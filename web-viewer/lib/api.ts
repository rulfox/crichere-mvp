/**
 * Shapes mirror the backend's public response DTOs exactly (docs/PHASE6.md):
 * `LeagueResponse`, `AuctionStateResponse`/`AuctionBidTickerResponse`, `AuctionResultsResponse`.
 * Kept as plain types, not re-derived from a schema -- this app has no code-sharing with the
 * Kotlin backend or the KMP mobile app (see docs/OVERVIEW.md), so the contract is copied by hand
 * and any drift shows up as a runtime shape mismatch, same as the mobile Ktor client's own DTOs.
 */

export type LeagueFranchise = {
  id: string;
  name: string;
  logoUrl: string | null;
};

export type PlayingRole = "BATSMAN" | "BOWLER" | "ALL_ROUNDER" | "WICKETKEEPER";
export type BattingStyle = "RIGHT_HAND" | "LEFT_HAND";
export type BowlingStyle =
  | "RIGHT_ARM_FAST"
  | "RIGHT_ARM_MEDIUM"
  | "RIGHT_ARM_OFFBREAK"
  | "RIGHT_ARM_LEGBREAK"
  | "LEFT_ARM_FAST"
  | "LEFT_ARM_MEDIUM"
  | "LEFT_ARM_ORTHODOX"
  | "LEFT_ARM_CHINAMAN";

export type LeaguePlayer = {
  id: string;
  name: string | null;
  playingRole?: PlayingRole | null;
};

export type League = {
  id: string;
  name: string;
  description?: string | null;
  logoUrl: string | null;
  bannerUrl: string | null;
  country: string;
  state: string;
  district: string;
  city: string;
  groundName?: string | null;
  startsOn: string;
  format: string | null;
  franchisesRequired?: number | null;
  players: LeaguePlayer[];
  franchises: LeagueFranchise[];
  auctionBasePrice: string | null;
  auctionPurse: string | null;
  auctionSquadMin: number | null;
  auctionSquadMax: number | null;
  /** Optional "bidding opens at" instant (docs/PHASE11.md D3). */
  auctionScheduledAt?: string | null;
};

export type AuctionStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

export type BidTickerRow = {
  franchiseId: string;
  franchiseName: string | null;
  amount: string;
  placedAt: string;
};

export type AuctionState = {
  auctionStatus: AuctionStatus;
  currentPlayerId: string | null;
  currentPlayerName: string | null;
  currentBidAmount: string | null;
  currentLeadingFranchiseId: string | null;
  currentLeadingFranchiseName: string | null;
  allowExceedPurse: boolean;
  recentBids: BidTickerRow[];
  currentPlayerPhotoUrl?: string | null;
  currentPlayerRole?: PlayingRole | null;
  currentPlayerBattingStyle?: BattingStyle | null;
  currentPlayerBowlingStyle?: BowlingStyle | null;
  /** "Lot N" -- how many players have been opened so far; `null` before the first (docs/PHASE11.md D2). */
  currentLotNumber?: number | null;
  playersTotal?: number;
  playersSold?: number;
  /** Players still in the pool next-player draws from. */
  playersPending?: number;
  /** The player just closed, while nobody is up yet -- the explicit SOLD/UNSOLD signal. */
  lastResult?: LastResult | null;
};

export type LastResult = {
  playerName: string | null;
  sold: boolean;
  franchiseName: string | null;
  amount: string | null;
};

export type FranchiseResult = {
  franchiseId: string;
  franchiseName: string;
  playersWon: { playerId: string; playerName: string | null; soldPrice: string; photoUrl?: string | null; playingRole?: PlayingRole | null }[];
  purseSpent: string;
  purseRemaining: string | null;
  belowSquadMin: boolean;
};

export type AuctionResults = {
  auctionStatus: AuctionStatus;
  franchises: FranchiseResult[];
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

const API_INTERNAL_BASE = process.env.API_INTERNAL_BASE_URL;

/**
 * Fetch made by this server (never the browser). Uses Railway's private network when
 * `API_INTERNAL_BASE_URL` is set (docs/PHASE14.md), skipping the public edge and TLS. If that call
 * can't connect -- the private network takes a few seconds to come up in a fresh container -- it
 * retries once over the public URL, so a link crawled right after a deploy still gets the real
 * league (chat apps cache whatever they get first). HTTP error statuses are returned, not retried.
 */
export async function serverFetch(path: string, init?: RequestInit): Promise<Response> {
  if (API_INTERNAL_BASE) {
    try {
      return await fetch(`${API_INTERNAL_BASE}${path}`, init);
    } catch {
      // Fall through to the public URL.
    }
  }
  return fetch(`${API_BASE}${path}`, init);
}

/** Server-side fetch of a league by id. `null` on a 404 -- the caller decides what that means (this app calls Next's `notFound()`). Never cached: an auction's readiness/state changes constantly. */
export async function fetchLeague(id: string): Promise<League | null> {
  const response = await serverFetch(`/api/v1/leagues/${id}`, { cache: "no-store" });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Couldn't load this league (${response.status}).`);
  return response.json();
}

/** Called from the browser (useAuctionStream), so it must use the public URL. */
export async function fetchResults(id: string): Promise<AuctionResults> {
  const response = await fetch(`${API_BASE}/api/v1/leagues/${id}/auction/results`, { cache: "no-store" });
  if (!response.ok) throw new Error(`Couldn't load results (${response.status}).`);
  return response.json();
}

/**
 * The league the landing page's "Watch live" links open (docs/PHASE11.md D5), or `null` when no
 * auction is in progress (204) or the lookup fails -- the links are simply hidden then. Revalidated
 * every 15s, matching the backend's own memo.
 */
export async function fetchLiveNow(): Promise<{ leagueId: string; leagueName: string } | null> {
  try {
    const response = await serverFetch("/api/v1/auctions/live-now", { next: { revalidate: 15 } });
    if (response.status !== 200) return null;
    return await response.json();
  } catch {
    return null;
  }
}

export function auctionStreamUrl(id: string): string {
  return `${API_BASE}/api/v1/leagues/${id}/auction/stream`;
}
