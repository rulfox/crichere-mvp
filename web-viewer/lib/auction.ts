/** Pure view-state/formatting helpers, kept out of components so they're unit-testable without a DOM (docs/PHASE6.md, docs/PHASE11.md). */

import type { AuctionResults, AuctionState, BattingStyle, BowlingStyle, PlayingRole } from "./api";

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

/** Real Indian Rupee grouping (e.g. ₹1,00,000), not a generic `$`/US-grouping default. */
export function formatInr(amount: string | number): string {
  return currency.format(Number(amount));
}

const grouping = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 });

/** en-IN digit grouping without a currency sign -- the landing stats (9,64,000). */
export function formatCount(value: number): string {
  return grouping.format(value);
}

const clock = new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit", second: "2-digit" });

export function formatBidTime(iso: string): string {
  return clock.format(new Date(iso));
}

/** The ticker's relative time: "just now" under 3s, then "14s ago", then "2m ago". */
export function formatAgo(iso: string, now: number): string {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (seconds < 3) return "just now";
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  return `${Math.floor(seconds / 3600)}h ago`;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** `2026-10-16` -> "16 Oct" -- parsed by hand so it's the same date in every time zone. */
export function formatStartsOn(isoDate: string): string {
  const [, month, day] = isoDate.split("-").map(Number);
  if (!month || !day) return isoDate;
  return `${day} ${MONTHS[month - 1]}`;
}

const scheduled = new Intl.DateTimeFormat("en-IN", {
  weekday: "short",
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

/** The organizer's scheduled bidding time in the viewer's own zone: "Sat 12 Oct, 7:00 pm". */
export function formatScheduled(iso: string): string {
  const parts = Object.fromEntries(scheduled.formatToParts(new Date(iso)).map((part) => [part.type, part.value]));
  return `${parts.weekday} ${parts.day} ${parts.month}, ${parts.hour}:${parts.minute} ${(parts.dayPeriod ?? "").toUpperCase()}`.trim();
}

const ROLE_LABELS: Record<PlayingRole, string> = {
  BATSMAN: "Batter",
  BOWLER: "Bowler",
  ALL_ROUNDER: "All-rounder",
  WICKETKEEPER: "Wicketkeeper",
};

const ROLE_SHORT: Record<PlayingRole, string> = {
  BATSMAN: "BAT",
  BOWLER: "BOWL",
  ALL_ROUNDER: "AR",
  WICKETKEEPER: "WK",
};

export const formatRole = (role: PlayingRole): string => ROLE_LABELS[role];
export const formatRoleShort = (role: PlayingRole): string => ROLE_SHORT[role];

export function formatBatting(style: BattingStyle): string {
  return style === "LEFT_HAND" ? "Left-hand bat" : "Right-hand bat";
}

const BOWLING_LABELS: Record<BowlingStyle, string> = {
  RIGHT_ARM_FAST: "Right-arm fast",
  RIGHT_ARM_MEDIUM: "Right-arm medium",
  RIGHT_ARM_OFFBREAK: "Right-arm off-break",
  RIGHT_ARM_LEGBREAK: "Right-arm leg-break",
  LEFT_ARM_FAST: "Left-arm fast",
  LEFT_ARM_MEDIUM: "Left-arm medium",
  LEFT_ARM_ORTHODOX: "Left-arm orthodox",
  LEFT_ARM_CHINAMAN: "Left-arm wrist spin",
};

export const formatBowling = (style: BowlingStyle): string => BOWLING_LABELS[style];

/**
 * Badge text for a franchise or player: the first letter of up to three words ("UK Kings" -> "UKK",
 * "Victory CC" -> "VCC"), or the first three letters of a single word ("Spartanz" -> "SPA").
 * The design's hand-picked short codes don't exist in the data, so this derives one.
 */
export function initials(name: string | null | undefined, singleWordLength = 3): string {
  const words = (name ?? "").trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "—";
  if (words.length === 1) return words[0].slice(0, singleWordLength).toUpperCase();
  return words
    .slice(0, 3)
    .map((word) => word[0])
    .join("")
    .toUpperCase();
}

const HUES = [25, 250, 300, 195, 145, 340];

/** A franchise's badge colour, by its position in the league's franchise list (stable across renders). */
export function franchiseColor(index: number): string {
  return `oklch(0.76 0.11 ${HUES[((index % HUES.length) + HUES.length) % HUES.length]})`;
}

export type ViewState = "not-started" | "live" | "completed";

export function viewStateFor(auctionStatus: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED"): ViewState {
  switch (auctionStatus) {
    case "NOT_STARTED":
      return "not-started";
    case "IN_PROGRESS":
      return "live";
    case "COMPLETED":
      return "completed";
  }
}

/** A just-closed lot, as the SOLD / UNSOLD band shows it. */
export type Outcome = {
  /** Identifies this closing, so the same one isn't shown twice. */
  key: string;
  sold: boolean;
  playerName: string | null;
  franchiseName: string | null;
  amount: string | null;
};

/**
 * The lot that just closed, from the backend's explicit `lastResult` (present while nobody is up yet).
 * [lotNumber] keys it so a repeat of the same state (e.g. a reconnect) doesn't replay the band.
 */
export function outcomeFromState(state: AuctionState): Outcome | null {
  const last = state.lastResult;
  if (!last || state.currentPlayerId) return null;
  return {
    key: `lot-${state.currentLotNumber ?? "?"}-${last.sold ? "sold" : "unsold"}`,
    sold: last.sold,
    playerName: last.playerName,
    franchiseName: last.franchiseName,
    amount: last.amount,
  };
}

/**
 * Fallback for a missed `lastResult` (the organizer opened the next player before this client saw
 * the in-between state): the previous player was SOLD if they now appear in a franchise's won
 * list, otherwise UNSOLD. Returns `null` when there was no previous player.
 */
export function detectOutcome(
  previous: { playerId: string; playerName: string | null; lotNumber: number | null | undefined } | null,
  results: AuctionResults | null,
): Outcome | null {
  if (!previous) return null;
  for (const franchise of results?.franchises ?? []) {
    const won = franchise.playersWon.find((player) => player.playerId === previous.playerId);
    if (won) {
      return {
        key: `lot-${previous.lotNumber ?? previous.playerId}-sold`,
        sold: true,
        playerName: won.playerName ?? previous.playerName,
        franchiseName: franchise.franchiseName,
        amount: won.soldPrice,
      };
    }
  }
  return {
    key: `lot-${previous.lotNumber ?? previous.playerId}-unsold`,
    sold: false,
    playerName: previous.playerName,
    franchiseName: null,
    amount: null,
  };
}
