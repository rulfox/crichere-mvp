import { describe, expect, it } from "vitest";
import type { AuctionState } from "./api";
import {
  detectOutcome,
  formatAgo,
  formatBatting,
  formatBowling,
  formatCount,
  formatInr,
  formatRole,
  formatRoleShort,
  formatStartsOn,
  franchiseColor,
  initials,
  outcomeFromState,
  viewStateFor,
} from "./auction";

describe("formatInr", () => {
  it("formats with Indian digit grouping, not US grouping", () => {
    expect(formatInr(100000)).toBe("₹1,00,000");
  });

  it("accepts a numeric string, matching what the backend serializes BigDecimal as", () => {
    expect(formatInr("900")).toBe("₹900");
  });

  it("drops fractional paise for a whole-rupee display", () => {
    expect(formatInr("150.00")).toBe("₹150");
  });
});

describe("formatCount", () => {
  it("groups the landing stats the Indian way", () => {
    expect(formatCount(964000)).toBe("9,64,000");
  });
});

describe("viewStateFor", () => {
  it("maps each backend auctionStatus to its view state", () => {
    expect(viewStateFor("NOT_STARTED")).toBe("not-started");
    expect(viewStateFor("IN_PROGRESS")).toBe("live");
    expect(viewStateFor("COMPLETED")).toBe("completed");
  });
});

describe("formatAgo", () => {
  const now = Date.parse("2026-10-02T12:00:00Z");
  it("reads like the design's ticker", () => {
    expect(formatAgo("2026-10-02T11:59:59Z", now)).toBe("just now");
    expect(formatAgo("2026-10-02T11:59:46Z", now)).toBe("14s ago");
    expect(formatAgo("2026-10-02T11:58:00Z", now)).toBe("2m ago");
    expect(formatAgo("2026-10-02T09:00:00Z", now)).toBe("3h ago");
  });
});

describe("formatStartsOn", () => {
  it("shows day and short month, the same in every time zone", () => {
    expect(formatStartsOn("2026-10-16")).toBe("16 Oct");
  });
});

describe("profile labels", () => {
  it("labels roles, short roles and styles", () => {
    expect(formatRole("ALL_ROUNDER")).toBe("All-rounder");
    expect(formatRoleShort("WICKETKEEPER")).toBe("WK");
    expect(formatBatting("LEFT_HAND")).toBe("Left-hand bat");
    expect(formatBowling("RIGHT_ARM_OFFBREAK")).toBe("Right-arm off-break");
  });
});

describe("initials", () => {
  it("takes the first letter of up to three words, or three letters of one word", () => {
    expect(initials("UK Kings")).toBe("UK");
    expect(initials("Mannancherry United Football Club")).toBe("MUF");
    expect(initials("Spartanz")).toBe("SPA");
    expect(initials("Aswin Sudarsanan", 2)).toBe("AS");
    expect(initials(null)).toBe("—");
  });
});

describe("franchiseColor", () => {
  it("cycles through the design's six hues", () => {
    expect(franchiseColor(0)).toBe("oklch(0.76 0.11 25)");
    expect(franchiseColor(6)).toBe("oklch(0.76 0.11 25)");
    expect(franchiseColor(1)).toBe("oklch(0.76 0.11 250)");
  });
});

const between: AuctionState = {
  auctionStatus: "IN_PROGRESS",
  currentPlayerId: null,
  currentPlayerName: null,
  currentBidAmount: null,
  currentLeadingFranchiseId: null,
  currentLeadingFranchiseName: null,
  allowExceedPurse: false,
  recentBids: [],
  currentLotNumber: 7,
};

describe("outcomeFromState", () => {
  it("reads the backend's explicit lastResult, keyed by lot", () => {
    const outcome = outcomeFromState({ ...between, lastResult: { playerName: "Sreeraj", sold: false, franchiseName: null, amount: null } });
    expect(outcome).toEqual({ key: "lot-7-unsold", sold: false, playerName: "Sreeraj", franchiseName: null, amount: null });
  });

  it("is null while a player is open or there is no last result", () => {
    expect(outcomeFromState(between)).toBeNull();
    expect(outcomeFromState({ ...between, currentPlayerId: "p1", lastResult: { playerName: "X", sold: true, franchiseName: "Y", amount: "1" } })).toBeNull();
  });
});

describe("detectOutcome", () => {
  const previous = { playerId: "p1", playerName: "Aswin Sudarsanan", lotNumber: 12 };
  const results = {
    auctionStatus: "IN_PROGRESS" as const,
    franchises: [
      { franchiseId: "f2", franchiseName: "Victory CC", playersWon: [{ playerId: "p1", playerName: "Aswin Sudarsanan", soldPrice: "120000" }], purseSpent: "120000", purseRemaining: "380000", belowSquadMin: true },
    ],
  };

  it("finds a closed player in a franchise's won list: SOLD, to whom, for how much", () => {
    expect(detectOutcome(previous, results)).toEqual({ key: "lot-12-sold", sold: true, playerName: "Aswin Sudarsanan", franchiseName: "Victory CC", amount: "120000" });
  });

  it("a closed player nobody won went UNSOLD", () => {
    expect(detectOutcome({ ...previous, playerId: "p9" }, results)?.sold).toBe(false);
  });

  it("nothing to report without a previous player", () => {
    expect(detectOutcome(null, results)).toBeNull();
  });
});
