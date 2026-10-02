"use client";

import { useEffect, useRef, useState } from "react";
import type { AuctionResults, AuctionState } from "@/lib/api";
import { auctionStreamUrl, fetchResults } from "@/lib/api";
import { detectOutcome, outcomeFromState, type Outcome } from "@/lib/auction";

/** How long the SOLD / UNSOLD band holds before the next player is shown (handoff README "Mapping"). */
export const OUTCOME_HOLD_MS = 3000;

/** Reconnect backoff when the browser's EventSource has given up (CLOSED), capped. */
const RETRY_DELAYS_MS = [2000, 4000, 8000, 15000];

export type Connection =
  | { status: "connecting" }
  | { status: "live" }
  | { status: "reconnecting"; attempt: number; lastUpdateAt: number | null };

export type StreamView = {
  /** The state the page renders. While an outcome band holds, this stays on the lot that closed. */
  display: AuctionState | null;
  /** The SOLD / UNSOLD band to show, if a lot just closed. */
  outcome: Outcome | null;
  results: AuctionResults | null;
  connection: Connection;
};

type OpenLot = { playerId: string; playerName: string | null; lotNumber: number | null | undefined; state: AuctionState };

/**
 * The live auction feed (docs/PHASE6.md SSE stream + results endpoint), plus what the redesign
 * layers on top (docs/PHASE11.md):
 *
 * - **Connection state**: `connecting` until the first `auction-state` event, `reconnecting` on
 *   EventSource errors (with an attempt count and the time of the last update), back to `live` on
 *   the next event. If the browser gives up (readyState CLOSED) a fresh EventSource is opened with backoff.
 * - **SOLD / UNSOLD moments**: the backend's explicit `lastResult` (present while nobody is up yet)
 *   drives the band. If that in-between state was missed -- the organizer opened the next player
 *   before this client saw it -- the closed player is looked up in the refreshed results instead
 *   ([detectOutcome]). Either way the band holds for [OUTCOME_HOLD_MS] on the closed lot, then the
 *   newest state is shown. The state already on screen when the page opens never plays a band.
 * - Results (standings / final squads) are refetched whenever the open player changes.
 */
export function useAuctionStream(leagueId: string): StreamView {
  const [view, setView] = useState<StreamView>({ display: null, outcome: null, results: null, connection: { status: "connecting" } });

  // Mutable feed bookkeeping, read and written only from event callbacks.
  const latest = useRef<AuctionState | null>(null);
  const lastOpen = useRef<OpenLot | null>(null);
  const shownOutcomes = useRef(new Set<string>());
  const holdUntil = useRef(0);
  const lastUpdateAt = useRef<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    let source: EventSource | null = null;
    let attempt = 0;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let holdTimer: ReturnType<typeof setTimeout> | undefined;
    let first = true;

    const refreshResults = () =>
      fetchResults(leagueId)
        .then((data) => {
          if (!cancelled) setView((v) => ({ ...v, results: data }));
          return data;
        })
        .catch(() => null); // Non-fatal -- the live state is still correct without standings.

    /** Shows [outcome] over the closed lot's state, then releases to whatever is newest. */
    const holdOutcome = (outcome: Outcome, closedLot: AuctionState) => {
      shownOutcomes.current.add(outcome.key);
      holdUntil.current = Date.now() + OUTCOME_HOLD_MS;
      setView((v) => ({ ...v, display: closedLot, outcome }));
      clearTimeout(holdTimer);
      holdTimer = setTimeout(() => {
        if (cancelled) return;
        holdUntil.current = 0;
        setView((v) => ({ ...v, display: latest.current, outcome: null }));
      }, OUTCOME_HOLD_MS);
    };

    const onState = (data: AuctionState) => {
      latest.current = data;
      lastUpdateAt.current = Date.now();
      attempt = 0;
      const previous = lastOpen.current;
      const playerChanged = (previous?.playerId ?? null) !== data.currentPlayerId;

      if (first) {
        // Whatever was already true when the page opened is history -- never replay it as a moment.
        first = false;
        const initial = outcomeFromState(data);
        if (initial) shownOutcomes.current.add(initial.key);
        if (data.currentPlayerId) lastOpen.current = openLot(data);
        setView((v) => ({ ...v, display: data, connection: { status: "live" } }));
        return;
      }

      setView((v) => ({ ...v, connection: { status: "live" } }));
      if (playerChanged) void refreshResults();

      const explicit = outcomeFromState(data);
      if (explicit && previous && !shownOutcomes.current.has(explicit.key)) {
        // The closed lot's card, finalised with the winning bid for a SOLD band.
        const closed = { ...previous.state, currentBidAmount: explicit.sold ? explicit.amount : previous.state.currentBidAmount };
        lastOpen.current = null;
        holdOutcome(explicit, closed);
        return;
      }

      if (data.currentPlayerId && previous && data.currentPlayerId !== previous.playerId) {
        // Next player opened without us seeing the in-between state: work out what happened.
        lastOpen.current = openLot(data);
        const closedState = previous.state;
        void fetchResults(leagueId)
          .then((fresh) => {
            if (cancelled) return;
            setView((v) => ({ ...v, results: fresh }));
            const fallback = detectOutcome(previous, fresh);
            if (fallback && !shownOutcomes.current.has(fallback.key)) {
              holdOutcome(fallback, { ...closedState, currentBidAmount: fallback.sold ? fallback.amount : closedState.currentBidAmount });
            } else if (Date.now() >= holdUntil.current) {
              setView((v) => ({ ...v, display: latest.current }));
            }
          })
          .catch(() => {
            if (!cancelled && Date.now() >= holdUntil.current) setView((v) => ({ ...v, display: latest.current }));
          });
        return;
      }

      if (data.currentPlayerId) lastOpen.current = openLot(data);
      if (Date.now() >= holdUntil.current) setView((v) => ({ ...v, display: data }));
    };

    const connect = () => {
      source = new EventSource(auctionStreamUrl(leagueId));
      source.addEventListener("auction-state", (event) => {
        if (cancelled) return;
        onState(JSON.parse((event as MessageEvent).data));
      });
      source.addEventListener("error", () => {
        if (cancelled || !source) return;
        attempt += 1;
        setView((v) => ({ ...v, connection: { status: "reconnecting", attempt, lastUpdateAt: lastUpdateAt.current } }));
        if (source.readyState === 2 /* CLOSED: the browser won't retry on its own */) {
          source.close();
          retryTimer = setTimeout(connect, RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)]);
        }
      });
    };

    void refreshResults();
    connect();

    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
      clearTimeout(holdTimer);
      source?.close();
    };
  }, [leagueId]);

  return view;
}

function openLot(state: AuctionState): OpenLot {
  return { playerId: state.currentPlayerId as string, playerName: state.currentPlayerName, lotNumber: state.currentLotNumber, state };
}
