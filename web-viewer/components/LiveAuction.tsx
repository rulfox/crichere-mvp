"use client";

import { useEffect, useRef, useState } from "react";
import type { AuctionResults, AuctionState, League } from "@/lib/api";
import { auctionStreamUrl, fetchResults } from "@/lib/api";
import { formatBidTime, formatInr, viewStateFor } from "@/lib/auction";
import styles from "./LiveAuction.module.css";

export function LiveAuction({ league }: { league: League }) {
  const [auction, setAuction] = useState<AuctionState | null>(null);
  const [results, setResults] = useState<AuctionResults | null>(null);
  const previousPlayerId = useRef<string | null | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;

    // Squads-so-far standings and the final results view both come from the same endpoint
    // (docs/PHASE6.md -- AuctionService.results() has no status guard, so it's a live snapshot
    // during IN_PROGRESS and the final one once COMPLETED). Refetch whenever the open player
    // changes -- that's the only time a franchise's squad/purse can have changed.
    const refreshResults = () => {
      fetchResults(league.id)
        .then((data) => {
          if (!cancelled) setResults(data);
        })
        .catch(() => {
          /* Non-fatal -- the live auction state above is still correct without it. */
        });
    };

    refreshResults();

    const source = new EventSource(auctionStreamUrl(league.id));
    source.addEventListener("auction-state", (event) => {
      const data: AuctionState = JSON.parse((event as MessageEvent).data);
      if (cancelled) return;
      setAuction(data);
      if (previousPlayerId.current !== undefined && previousPlayerId.current !== data.currentPlayerId) {
        refreshResults();
      }
      previousPlayerId.current = data.currentPlayerId;
    });

    return () => {
      cancelled = true;
      source.close();
    };
  }, [league.id]);

  if (!auction) {
    return (
      <div className={styles.page}>
        <StatusBar league={league} live={false} />
        <p className={styles.noticeBody}>Connecting to the live auction&hellip;</p>
      </div>
    );
  }

  const view = viewStateFor(auction.auctionStatus);

  return (
    <div className={styles.page}>
      <StatusBar league={league} live={view === "live"} />

      {view === "not-started" && <NotStarted league={league} />}
      {view === "live" && <Live league={league} auction={auction} results={results} />}
      {view === "completed" && <Completed results={results} />}

      <footer className={styles.footer}>
        <p>Public link, no account needed to follow along.</p>
      </footer>
    </div>
  );
}

function StatusBar({ league, live }: { league: League; live: boolean }) {
  return (
    <header className={styles.status}>
      {live && <span className={styles.liveDot} aria-hidden="true" />}
      {live && <span className={styles.liveLabel}>Live</span>}
      <span className={styles.leagueName}>{league.name}</span>
      <span className={styles.leagueMeta}>
        {league.city}, {league.state}
      </span>
    </header>
  );
}

function NotStarted({ league }: { league: League }) {
  return (
    <div className={styles.notice}>
      <h1 className={styles.noticeTitle}>The auction hasn&rsquo;t started yet</h1>
      <p className={styles.noticeBody}>
        This page updates the moment the organizer starts bidding &mdash; leave it open, or come back later.
      </p>
      <div className={styles.leagueFacts}>
        {league.format && <span>{league.format}</span>}
        <span>Starts {league.startsOn}</span>
        {league.auctionBasePrice && <span>Base price {formatInr(league.auctionBasePrice)}</span>}
        {league.auctionPurse && <span>Purse {formatInr(league.auctionPurse)}</span>}
      </div>
    </div>
  );
}

function Live({ league, auction, results }: { league: League; auction: AuctionState; results: AuctionResults | null }) {
  return (
    <div className={styles.layout}>
      <div className={styles.heroCol}>
        <article className={styles.onBlock}>
          {auction.currentPlayerId ? (
            <>
              <p className={styles.onBlockLabel}>On the block</p>
              <h1 className={styles.playerName}>{auction.currentPlayerName ?? "Unnamed player"}</h1>
              {league.auctionBasePrice && <p className={styles.playerMeta}>Base price {formatInr(league.auctionBasePrice)}</p>}
              <div className={styles.bidRow}>
                {auction.currentBidAmount ? (
                  <span className={styles.bidAmount}>{formatInr(auction.currentBidAmount)}</span>
                ) : (
                  <span className={styles.bidNone}>No bids yet</span>
                )}
              </div>
              {auction.currentLeadingFranchiseName && (
                <p className={styles.leadingChip}>
                  <span className={styles.chipDot} aria-hidden="true" />
                  {auction.currentLeadingFranchiseName} leading
                </p>
              )}
            </>
          ) : (
            <>
              <p className={styles.onBlockLabel}>Between players</p>
              <h1 className={styles.playerName}>Waiting for the next player</h1>
            </>
          )}
        </article>

        <section aria-label="Recent bids">
          <p className={styles.sectionLabel}>Recent bids</p>
          {auction.recentBids.length > 0 ? (
            <ol className={styles.tickerList}>
              {auction.recentBids.map((bid, index) => (
                <li className={styles.tickerRow} key={`${bid.franchiseId}-${bid.placedAt}-${index}`}>
                  <span className={styles.tickerFranchise}>{bid.franchiseName ?? "Unknown franchise"}</span>
                  <span className={styles.tickerAmount}>{formatInr(bid.amount)}</span>
                  <span>{formatBidTime(bid.placedAt)}</span>
                </li>
              ))}
            </ol>
          ) : (
            <p className={styles.tickerEmpty}>No bids on this player yet.</p>
          )}
        </section>
      </div>

      <aside className={styles.standingsCol} aria-label="Squads so far">
        <p className={styles.sectionLabel}>Squads so far</p>
        <div className={styles.standings}>
          {results?.franchises.map((franchise) => (
            <StandingRow key={franchise.franchiseId} franchise={franchise} leading={franchise.franchiseId === auction.currentLeadingFranchiseId} />
          ))}
        </div>
      </aside>
    </div>
  );
}

function StandingRow({ franchise, leading }: { franchise: AuctionResults["franchises"][number]; leading: boolean }) {
  const purse = franchise.purseRemaining;
  const spent = Number(franchise.purseSpent);
  const total = purse !== null ? Number(purse) + spent : null;
  const remainingPct = total && total > 0 ? Math.max(0, Math.min(100, (Number(purse) / total) * 100)) : 100;

  return (
    <div className={`${styles.franchiseRow} ${leading ? styles.leading : ""}`}>
      <div className={styles.franchiseTop}>
        <span className={styles.franchiseName}>{franchise.franchiseName}</span>
        <span className={styles.franchiseCount}>
          {franchise.playersWon.length} player{franchise.playersWon.length === 1 ? "" : "s"}
        </span>
      </div>
      {purse !== null && (
        <>
          <div className={styles.purseBar}>
            <div className={styles.purseBarFill} style={{ width: `${remainingPct}%` }} />
          </div>
          <p className={styles.purseLabel}>{formatInr(purse)} left</p>
        </>
      )}
      {franchise.belowSquadMin && <span className={styles.belowMin}>Below minimum squad size</span>}
    </div>
  );
}

function Completed({ results }: { results: AuctionResults | null }) {
  if (!results) {
    return <p className={styles.noticeBody}>Loading final results&hellip;</p>;
  }

  return (
    <div>
      <h1 className={styles.noticeTitle} style={{ marginBottom: 16 }}>
        Final results
      </h1>
      <div className={styles.resultsList}>
        {results.franchises.map((franchise) => (
          <div className={styles.resultRow} key={franchise.franchiseId}>
            <div className={styles.resultTop}>
              <span className={styles.franchiseName}>{franchise.franchiseName}</span>
              <span className={styles.resultSpent}>
                Spent {formatInr(franchise.purseSpent)}
                {franchise.purseRemaining !== null && ` · ${formatInr(franchise.purseRemaining)} left`}
              </span>
            </div>
            {franchise.belowSquadMin && <span className={styles.belowMin}>Below minimum squad size</span>}
            {franchise.playersWon.length > 0 ? (
              <ul className={styles.playerList}>
                {franchise.playersWon.map((player) => (
                  <li className={styles.playerRow} key={player.playerId}>
                    <span>{player.playerName ?? "Unnamed player"}</span>
                    <span>{formatInr(player.soldPrice)}</span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className={styles.tickerEmpty}>No players won.</p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
