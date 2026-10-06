import { act, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { AuctionResults, AuctionState, League } from "@/lib/api";
import { LiveAuction } from "./LiveAuction";
import { OUTCOME_HOLD_MS } from "./useAuctionStream";

const league: League = {
  id: "l1",
  name: "Spartanz Premier League",
  logoUrl: null,
  bannerUrl: null,
  country: "India",
  state: "Kerala",
  district: "Alappuzha",
  groundName: "Udhaya Ground",
  startsOn: "2026-10-16",
  format: "T10",
  franchisesRequired: 2,
  players: [],
  franchises: [
    { id: "f1", name: "Spartanz", logoUrl: null },
    { id: "f2", name: "Victory CC", logoUrl: null },
  ],
  auctionBasePrice: "20000",
  auctionPurse: "500000",
  auctionSquadMin: 4,
  auctionSquadMax: 6,
  auctionScheduledAt: null,
};

const idle: AuctionState = {
  auctionStatus: "IN_PROGRESS",
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
};

const openLot: AuctionState = {
  ...idle,
  currentPlayerId: "p1",
  currentPlayerName: "Aswin Sudarsanan",
  currentPlayerRole: "ALL_ROUNDER",
  currentPlayerBattingStyle: null,
  currentPlayerBowlingStyle: null,
  currentLotNumber: 12,
  playersPending: 19,
  currentBidAmount: "120000",
  currentLeadingFranchiseId: "f2",
  currentLeadingFranchiseName: "Victory CC",
  recentBids: [{ franchiseId: "f2", franchiseName: "Victory CC", amount: "120000", placedAt: "2026-09-13T10:00:00Z" }],
};

const standings = (wonByVictory: { playerId: string; playerName: string; soldPrice: string }[] = []): AuctionResults => ({
  auctionStatus: "IN_PROGRESS",
  franchises: [
    { franchiseId: "f1", franchiseName: "Spartanz", playersWon: [], purseSpent: "0", purseRemaining: "500000", belowSquadMin: true },
    { franchiseId: "f2", franchiseName: "Victory CC", playersWon: wonByVictory, purseSpent: "0", purseRemaining: "500000", belowSquadMin: true },
  ],
});

/** A minimal fake standing in for the browser's EventSource -- the seam is the constructor itself (see docs/PHASE6.md). */
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  listeners = new Map<string, (event: MessageEvent) => void>();
  readyState = 1;
  closed = false;
  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }
  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    this.listeners.set(type, listener);
  }
  close() {
    this.closed = true;
  }
  emit(type: string, data?: unknown) {
    act(() => this.listeners.get(type)?.({ data: JSON.stringify(data) } as MessageEvent));
  }
}

let resultsResponse: AuctionResults = standings();

beforeEach(() => {
  FakeEventSource.instances = [];
  resultsResponse = standings();
  vi.stubGlobal("EventSource", FakeEventSource as unknown as typeof EventSource);
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation(async () => ({ ok: true, status: 200, json: async () => resultsResponse })),
  );
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

const source = () => FakeEventSource.instances[FakeEventSource.instances.length - 1];

describe("LiveAuction", () => {
  it("shows the loading skeleton until the first stream event arrives", () => {
    render(<LiveAuction league={league} />);

    expect(screen.getByLabelText("Loading league")).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText("Connecting to the live auction…")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
  });

  it("not started: shows the waiting notice with the scheduled time and the auction facts", async () => {
    render(<LiveAuction league={{ ...league, auctionScheduledAt: "2026-10-12T13:30:00Z" }} />);
    source().emit("auction-state", { ...idle, auctionStatus: "NOT_STARTED" });

    expect(await screen.findByRole("heading", { name: /hasn.t started yet/ })).toBeInTheDocument();
    expect(screen.getByText("NOT STARTED")).toBeInTheDocument();
    // W10 (design update #6): one location line, "Ground, District, State".
    expect(screen.getByText("Udhaya Ground, Alappuzha, Kerala")).toBeInTheDocument();
    expect(screen.getByText(/Bidding is scheduled for/)).toBeInTheDocument();
    const facts = screen.getByLabelText("Auction facts");
    expect(within(facts).getByText("₹20,000")).toBeInTheDocument();
    expect(within(facts).getByText("4–6")).toBeInTheDocument();
    expect(within(facts).getByText(/2 of 2 claimed/)).toBeInTheDocument();
  });

  it("not started without a scheduled time leaves the time out", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", { ...idle, auctionStatus: "NOT_STARTED" });

    await screen.findByRole("heading", { name: /hasn.t started yet/ });
    expect(screen.queryByText(/Bidding is scheduled for/)).not.toBeInTheDocument();
  });

  it("live: the player under the hammer, lot, bid, leader, ticker and standings", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", openLot);

    expect(await screen.findByRole("heading", { name: "Aswin Sudarsanan" })).toBeInTheDocument();
    expect(screen.getByText("LIVE")).toBeInTheDocument();
    expect(screen.getByText("Lot 12 · 18 left in pool")).toBeInTheDocument();
    expect(screen.getByText("Current bid")).toBeInTheDocument();
    expect(screen.getByLabelText("₹1,20,000")).toBeInTheDocument();
    expect(within(screen.getByLabelText("Player under the hammer")).getByText("Victory CC")).toBeInTheDocument();
    expect(within(screen.getByLabelText("Recent bids")).getByText("Victory CC")).toBeInTheDocument();
    await waitFor(() => expect(within(screen.getByLabelText("Franchise standings")).getByText("LEADING")).toBeInTheDocument());
  });

  it("live: hides the batting/bowling chips the profile doesn't have, keeps the role", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", openLot);

    await screen.findByRole("heading", { name: "Aswin Sudarsanan" });
    expect(screen.getByText("All-rounder")).toBeInTheDocument();
    expect(screen.queryByText(/hand bat/)).not.toBeInTheDocument();
    expect(screen.queryByText(/arm/)).not.toBeInTheDocument();
  });

  it("live: shows both style chips when the profile has them", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", { ...openLot, currentPlayerBattingStyle: "LEFT_HAND", currentPlayerBowlingStyle: "LEFT_ARM_ORTHODOX" });

    expect(await screen.findByText("Left-hand bat")).toBeInTheDocument();
    expect(screen.getByText("Left-arm orthodox")).toBeInTheDocument();
  });

  it("SOLD: the backend's lastResult shows the band, held, then the page moves on", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", openLot);
    await screen.findByRole("heading", { name: "Aswin Sudarsanan" });

    vi.useFakeTimers();
    source().emit("auction-state", {
      ...idle,
      currentLotNumber: 12,
      lastResult: { playerName: "Aswin Sudarsanan", sold: true, franchiseName: "Victory CC", amount: "120000" },
    });

    const band = screen.getByRole("status");
    expect(band).toHaveTextContent("SOLD");
    expect(band).toHaveTextContent("to Victory CC");
    expect(band).toHaveTextContent("₹1,20,000");
    expect(screen.getByText("Winning bid")).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(OUTCOME_HOLD_MS));
    expect(screen.queryByText("SOLD")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Next player coming up" })).toBeInTheDocument();
  });

  it("UNSOLD: the band says the player returns to the pool", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", { ...openLot, currentBidAmount: null, currentLeadingFranchiseId: null, currentLeadingFranchiseName: null, recentBids: [] });
    await screen.findByRole("heading", { name: "Aswin Sudarsanan" });

    source().emit("auction-state", {
      ...idle,
      currentLotNumber: 12,
      lastResult: { playerName: "Aswin Sudarsanan", sold: false, franchiseName: null, amount: null },
    });

    expect(screen.getByRole("status")).toHaveTextContent("UNSOLD");
    expect(screen.getByText("Aswin Sudarsanan returns to the pool")).toBeInTheDocument();
    expect(screen.getByText("Closed at base price")).toBeInTheDocument();
  });

  it("a lot that was already closed when the page opened never replays its band", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", {
      ...idle,
      currentLotNumber: 12,
      lastResult: { playerName: "Aswin Sudarsanan", sold: true, franchiseName: "Victory CC", amount: "120000" },
    });

    expect(await screen.findByRole("heading", { name: "Next player coming up" })).toBeInTheDocument();
    expect(screen.queryByText("SOLD")).not.toBeInTheDocument();
  });

  it("between lots: the next lot, the last result and the progress (design update #4 W2)", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", {
      ...idle,
      currentLotNumber: 12,
      playersTotal: 57,
      playersPending: 45,
      lastResult: { playerName: "Rohan Patil", sold: true, franchiseName: "Victory CC", amount: "15000" },
    });

    const card = await screen.findByLabelText("Between lots");
    expect(within(card).getByText("Lot 13 · 45 left in pool")).toBeInTheDocument();
    expect(within(card).getByRole("heading", { name: "Next player coming up" })).toBeInTheDocument();
    expect(within(card).getByText(/Last: Rohan Patil/)).toHaveTextContent("Last: Rohan Patil sold to Victory CC for ₹15,000.");
    expect(within(card).getByRole("progressbar", { name: "Lots done" })).toHaveAttribute("aria-valuenow", "12");
    expect(within(card).getByText("12 of 57 lots done")).toBeInTheDocument();
  });

  it("between lots after an unsold player says so", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", { ...idle, lastResult: { playerName: "Rohan Patil", sold: false, franchiseName: null, amount: null } });

    expect(await screen.findByText(/Last: Rohan Patil/)).toHaveTextContent("Last: Rohan Patil went unsold.");
  });

  it("nobody can bid: the card says bidding has closed instead of promising a next player (W3)", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", { ...idle, playersPending: 28, canAnyoneBid: false });

    const card = await screen.findByLabelText("Bidding closed");
    expect(within(card).getByRole("heading", { name: "Waiting for the organizer" })).toBeInTheDocument();
    expect(within(card).getByText("28 left in pool")).toBeInTheDocument();
    expect(within(card).getByText(/Final results appear here when the auction ends/)).toBeInTheDocument();
    expect(screen.queryByText("Next player coming up")).not.toBeInTheDocument();
    expect(within(card).queryByRole("progressbar")).not.toBeInTheDocument();
  });

  it("a missed in-between state: the closed player found in the refreshed results is shown as SOLD", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", openLot);
    await screen.findByRole("heading", { name: "Aswin Sudarsanan" });

    resultsResponse = standings([{ playerId: "p1", playerName: "Aswin Sudarsanan", soldPrice: "120000" }]);
    source().emit("auction-state", { ...openLot, currentPlayerId: "p2", currentPlayerName: "Tony Jose", currentLotNumber: 13, currentBidAmount: null, recentBids: [] });

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("SOLD"));
    expect(screen.getByRole("status")).toHaveTextContent("to Victory CC");
  });

  it("reconnecting: a stream error shows the banner and dims the live content", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", openLot);
    await screen.findByRole("heading", { name: "Aswin Sudarsanan" });

    source().readyState = 0; // CONNECTING -- the browser is retrying on its own
    source().emit("error");

    expect(screen.getByText("Connection lost. Reconnecting…")).toBeInTheDocument();
    expect(screen.getByText(/attempt 1/)).toBeInTheDocument();
    expect(screen.getByText("Reconnecting")).toBeInTheDocument();

    source().readyState = 1;
    source().emit("auction-state", openLot);
    expect(screen.queryByText("Connection lost. Reconnecting…")).not.toBeInTheDocument();
    expect(screen.getByText("Live feed")).toBeInTheDocument();
  });

  it("reconnecting: when the browser gives up, a new stream is opened after a backoff", async () => {
    render(<LiveAuction league={league} />);
    source().emit("auction-state", openLot);
    await screen.findByRole("heading", { name: "Aswin Sudarsanan" });

    vi.useFakeTimers();
    const first = source();
    first.readyState = 2; // CLOSED
    first.emit("error");
    expect(first.closed).toBe(true);
    expect(FakeEventSource.instances).toHaveLength(1);

    act(() => vi.advanceTimersByTime(2000));
    expect(FakeEventSource.instances).toHaveLength(2);
  });

  it("completed: summary strip and per-franchise squads, flagging a squad below the minimum", async () => {
    resultsResponse = {
      auctionStatus: "COMPLETED",
      franchises: [
        {
          franchiseId: "f1",
          franchiseName: "Spartanz",
          playersWon: [{ playerId: "p1", playerName: "Aswin Sudarsanan", soldPrice: "120000", playingRole: "ALL_ROUNDER" }],
          purseSpent: "120000",
          purseRemaining: "380000",
          belowSquadMin: true,
        },
      ],
    };
    render(<LiveAuction league={league} />);
    source().emit("auction-state", { ...idle, auctionStatus: "COMPLETED", playersSold: 1 });

    expect(await screen.findByRole("heading", { name: "Auction complete" })).toBeInTheDocument();
    expect(screen.getByText("COMPLETED")).toBeInTheDocument();
    expect(screen.getByText("29")).toBeInTheDocument(); // unsold = 30 in the pool - 1 sold
    expect(screen.getByText("Aswin Sudarsanan")).toBeInTheDocument();
    expect(screen.getByText("AR")).toBeInTheDocument();
    expect(screen.getByText("Below squad minimum · 1 of 4")).toBeInTheDocument();
  });

  it("live standings over purse: 'Over purse' with the amount, never a negative number (U5 W5)", async () => {
    resultsResponse = {
      auctionStatus: "IN_PROGRESS",
      franchises: [
        { franchiseId: "f1", franchiseName: "Spartanz", playersWon: [], purseSpent: "0", purseRemaining: "0", belowSquadMin: true },
        { franchiseId: "f2", franchiseName: "Victory CC", playersWon: [], purseSpent: "525000", purseRemaining: "-25000", belowSquadMin: true },
      ],
    };
    render(<LiveAuction league={league} />);
    source().emit("auction-state", openLot);

    const table = await screen.findByLabelText("Franchise standings");
    await waitFor(() => expect(within(table).getByText("Over purse by ₹25,000")).toBeInTheDocument());
    expect(within(table).getByText("Over purse")).toBeInTheDocument();
    // ₹0 left stays the normal line.
    expect(within(table).getByText("₹0")).toBeInTheDocument();
    expect(within(table).queryByText(/-/)).not.toBeInTheDocument();
    // The leading franchise keeps its tag: gold owns the bid, alert owns the money.
    expect(within(table).getByText("LEADING")).toBeInTheDocument();
  });

  it("completed over purse: the Purse left tile becomes Over purse (U5 W6)", async () => {
    resultsResponse = {
      auctionStatus: "COMPLETED",
      franchises: [
        {
          franchiseId: "f1",
          franchiseName: "Spartanz",
          playersWon: [{ playerId: "p1", playerName: "Aswin Sudarsanan", soldPrice: "525000", playingRole: "ALL_ROUNDER" }],
          purseSpent: "525000",
          purseRemaining: "-25000",
          belowSquadMin: false,
        },
      ],
    };
    render(<LiveAuction league={league} />);
    source().emit("auction-state", { ...idle, auctionStatus: "COMPLETED", playersSold: 1 });

    expect(await screen.findByText("Over purse")).toBeInTheDocument();
    expect(screen.getByText("₹25,000")).toBeInTheDocument();
    expect(screen.queryByText("Purse left")).not.toBeInTheDocument();
  });
});
