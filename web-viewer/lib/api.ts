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

export type LeaguePlayer = {
  id: string;
  name: string | null;
};

export type League = {
  id: string;
  name: string;
  logoUrl: string | null;
  bannerUrl: string | null;
  country: string;
  state: string;
  district: string;
  city: string;
  startsOn: string;
  format: string | null;
  players: LeaguePlayer[];
  franchises: LeagueFranchise[];
  auctionBasePrice: string | null;
  auctionPurse: string | null;
  auctionSquadMin: number | null;
  auctionSquadMax: number | null;
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
};

export type FranchiseResult = {
  franchiseId: string;
  franchiseName: string;
  playersWon: { playerId: string; playerName: string | null; soldPrice: string }[];
  purseSpent: string;
  purseRemaining: string | null;
  belowSquadMin: boolean;
};

export type AuctionResults = {
  auctionStatus: AuctionStatus;
  franchises: FranchiseResult[];
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** Server-side fetch of a league by id. `null` on a 404 -- the caller decides what that means (this app calls Next's `notFound()`). Never cached: an auction's readiness/state changes constantly. */
export async function fetchLeague(id: string): Promise<League | null> {
  const response = await fetch(`${API_BASE}/api/v1/leagues/${id}`, { cache: "no-store" });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`Couldn't load this league (${response.status}).`);
  return response.json();
}

export async function fetchResults(id: string): Promise<AuctionResults> {
  const response = await fetch(`${API_BASE}/api/v1/leagues/${id}/auction/results`, { cache: "no-store" });
  if (!response.ok) throw new Error(`Couldn't load results (${response.status}).`);
  return response.json();
}

export function auctionStreamUrl(id: string): string {
  return `${API_BASE}/api/v1/leagues/${id}/auction/stream`;
}
