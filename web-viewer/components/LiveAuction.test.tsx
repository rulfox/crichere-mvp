import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AuctionResults, League } from "@/lib/api";
import { LiveAuction } from "./LiveAuction";

const league: League = {
  id: "l1",
  name: "Phase6 Test League",
  logoUrl: null,
  bannerUrl: null,
  country: "India",
  state: "Bihar",
  district: "Patna",
  city: "Patna",
  startsOn: "2026-09-13",
  format: "T20",
  players: [],
  franchises: [],
  auctionBasePrice: "50",
  auctionPurse: "1000",
  auctionSquadMin: 1,
  auctionSquadMax: 5,
};

const emptyResults: AuctionResults = { auctionStatus: "NOT_STARTED", franchises: [] };

/** A minimal fake standing in for the browser's EventSource -- this repo's test methodology
 * (see AuctionServiceTest.kt/AuctionViewModelTest.kt) prefers driving real production code
 * through its real seams over mocking deep internals; the seam here is the constructor itself. */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  listeners = new Map<string, (event: MessageEvent) => void>();
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    this.listeners.set(type, listener);
  }
  close() {}
  emit(type: string, data: unknown) {
    this.listeners.get(type)?.({ data: JSON.stringify(data) } as MessageEvent);
  }
}

beforeEach(() => {
  FakeEventSource.instances = [];
  vi.stubGlobal("EventSource", FakeEventSource as unknown as typeof EventSource);
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => emptyResults }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("LiveAuction", () => {
  it("shows a connecting notice before the first stream event arrives", () => {
    render(<LiveAuction league={league} />);
    expect(screen.getByText(/connecting to the live auction/i)).toBeInTheDocument();
  });

  it("renders the not-started state -- league info and a waiting notice, no bid UI", async () => {
    render(<LiveAuction league={league} />);
    const source = FakeEventSource.instances[0];
    source.emit("auction-state", {
      auctionStatus: "NOT_STARTED",
      currentPlayerId: null,
      currentPlayerName: null,
      currentBidAmount: null,
      currentLeadingFranchiseId: null,
      currentLeadingFranchiseName: null,
      allowExceedPurse: false,
      recentBids: [],
    });

    await waitFor(() => expect(screen.getByText(/hasn.t started yet/i)).toBeInTheDocument());
    expect(screen.queryByText(/on the block/i)).not.toBeInTheDocument();
  });

  it("renders the live state -- current player, current bid, and the leading franchise", async () => {
    render(<LiveAuction league={league} />);
    const source = FakeEventSource.instances[0];
    source.emit("auction-state", {
      auctionStatus: "IN_PROGRESS",
      currentPlayerId: "p1",
      currentPlayerName: "Test Player",
      currentBidAmount: "100",
      currentLeadingFranchiseId: "f1",
      currentLeadingFranchiseName: "Bihar2",
      allowExceedPurse: false,
      recentBids: [{ franchiseId: "f1", franchiseName: "Bihar2", amount: "100", placedAt: "2026-09-13T10:00:00Z" }],
    });

    await waitFor(() => expect(screen.getByText("Test Player")).toBeInTheDocument());
    // ₹100 renders twice by design -- the hero bid figure and the ticker's own row for that same bid.
    expect(screen.getAllByText("₹100")).toHaveLength(2);
    expect(screen.getByText(/Bihar2 leading/)).toBeInTheDocument();
  });

  it("renders the completed state -- final per-franchise results", async () => {
    const finalResults: AuctionResults = {
      auctionStatus: "COMPLETED",
      franchises: [
        {
          franchiseId: "f1",
          franchiseName: "Bihar2",
          playersWon: [{ playerId: "p1", playerName: "Test Player", soldPrice: "100" }],
          purseSpent: "100",
          purseRemaining: "900",
          belowSquadMin: false,
        },
      ],
    };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => finalResults }));

    render(<LiveAuction league={league} />);
    const source = FakeEventSource.instances[0];
    source.emit("auction-state", {
      auctionStatus: "COMPLETED",
      currentPlayerId: null,
      currentPlayerName: null,
      currentBidAmount: null,
      currentLeadingFranchiseId: null,
      currentLeadingFranchiseName: null,
      allowExceedPurse: false,
      recentBids: [],
    });

    await waitFor(() => expect(screen.getByText(/final results/i)).toBeInTheDocument());
    expect(screen.getByText("Bihar2")).toBeInTheDocument();
    expect(screen.getByText("Test Player")).toBeInTheDocument();
  });
});
