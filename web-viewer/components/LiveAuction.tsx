"use client";

import Link from "next/link";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { AuctionResults, AuctionState, FranchiseResult, League } from "@/lib/api";
import {
  formatAgo,
  formatBatting,
  formatBowling,
  formatInr,
  formatRole,
  formatRoleShort,
  formatScheduled,
  formatStartsOn,
  franchiseColor,
  initials,
  type Outcome,
  viewStateFor,
} from "@/lib/auction";
import { animate, bounce, EXPO_OUT, prefersReducedMotion, springSoft, useReducedMotion } from "@/lib/motion";
import { PLAY_STORE_URL } from "@/lib/store";
import { GetAppPopover } from "./ui/GetAppPopover";
import { Icon } from "./ui/Icon";
import { StoreBadge } from "./ui/StoreBadge";
import { type Connection, useAuctionStream } from "./useAuctionStream";
import styles from "./LiveAuction.module.css";

/** Live Auction.dc.html -- every state of the public, no-login auction page (docs/PHASE11.md). */
export function LiveAuction({ league }: { league: League }) {
  const { display, outcome, results, connection } = useAuctionStream(league.id);
  const view = display ? viewStateFor(display.auctionStatus) : null;
  const reconnecting = connection.status === "reconnecting";

  return (
    <div data-surface="dark" className={styles.root}>
      <div aria-hidden="true" className={styles.backdrop}>
        <div className={styles.glowGold} />
        <div className={styles.glowGreen} />
      </div>

      <TopBar connection={connection} showFeed={view === "live"} />
      {connection.status === "reconnecting" && <ReconnectBanner connection={connection} />}

      <main className={styles.main}>
        {!display || !view ? (
          <Skeleton />
        ) : (
          <>
            <LeagueHeader league={league} view={view} />
            {view === "not-started" && <NotStarted league={league} />}
            {view === "live" && <Live league={league} auction={display} results={results} outcome={outcome} dimmed={reconnecting} />}
            {view === "completed" && <Completed league={league} auction={display} results={results} />}
          </>
        )}
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerBar}>
          <span>Public watch link · no account needed to follow along.</span>
          <Link href="/" className={styles.footerLink}>
            Powered by Crichere
          </Link>
        </div>
      </footer>

      <AppBanner />
    </div>
  );
}

// ---------------------------------------------------------------- chrome

/**
 * Design update #4 W1: the white wordmark (22px, 24px from 768), and below 480px the short labels
 * ("Live", "Offline", "Get app") so nothing wraps at 360px. Both label lengths are in the DOM; CSS
 * container queries pick one.
 */
function TopBar({ connection, showFeed }: { connection: Connection; showFeed: boolean }) {
  return (
    <header className={styles.topBar}>
      <div className={styles.bar}>
        <Link href="/" aria-label="Crichere home" className={styles.logo}>
          {/* eslint-disable-next-line @next/next/no-img-element -- static SVG wordmark */}
          <img src="/crichere-wordmark-white.svg" alt="Crichere" className={styles.wordmark} />
        </Link>
        <div className={styles.barRight}>
          {connection.status === "reconnecting" ? (
            <span className={styles.feedLost}>
              <span aria-hidden="true" className={styles.spinRing} />
              <span className={styles.labelWide}>Reconnecting</span>
              <span className={styles.labelNarrow} aria-hidden="true">
                Offline
              </span>
            </span>
          ) : (
            showFeed &&
            connection.status === "live" && (
              <span className={styles.feed} title="Receiving live updates">
                <span className={styles.feedDot} />
                <span className={styles.labelWide}>Live feed</span>
                <span className={styles.labelNarrow} aria-hidden="true">
                  Live
                </span>
              </span>
            )
          )}
          <GetAppPopover />
        </div>
      </div>
    </header>
  );
}

function ReconnectBanner({ connection }: { connection: Extract<Connection, { status: "reconnecting" }> }) {
  const now = useNow(1000);
  const since = connection.lastUpdateAt ? Math.max(0, Math.round((now - connection.lastUpdateAt) / 1000)) : null;
  return (
    <div role="status" className={styles.reconnect}>
      <div className={styles.reconnectBar}>
        <Icon name="sync" size={20} className={`${styles.reconnectIcon} ${styles.spin}`} />
        <span className={styles.reconnectTitle}>Connection lost. Reconnecting…</span>
        <span className={styles.reconnectMeta}>
          {since !== null ? `Last update ${since}s ago · ` : ""}attempt {connection.attempt}
        </span>
      </div>
    </div>
  );
}

function Skeleton() {
  return (
    <div aria-busy="true" aria-label="Loading league" className={styles.skeleton}>
      <div className={styles.skelHead}>
        <div className={`${styles.skel} ${styles.skelBright}`} style={{ width: 64, height: 64, borderRadius: 16 }} />
        <div className={styles.skelLines}>
          <div className={`${styles.skel} ${styles.skelBright}`} style={{ width: "min(420px, 80%)", height: 28, borderRadius: 8 }} />
          <div className={`${styles.skel} ${styles.skelBright}`} style={{ width: "min(300px, 60%)", height: 14, borderRadius: 6 }} />
        </div>
      </div>
      <div className={styles.twoCol} style={{ alignItems: "stretch" }}>
        <div className={styles.heroCol}>
          <div className={styles.skel} style={{ height: 380, borderRadius: 16 }} />
          <div className={styles.skel} style={{ height: 220, borderRadius: 16 }} />
        </div>
        <div className={styles.skel} style={{ flex: "1 1 320px", minWidth: 0, height: 620, borderRadius: 16 }} />
      </div>
      <span className={styles.skelNote}>Connecting to the live auction…</span>
    </div>
  );
}

function LeagueHeader({ league, view }: { league: League; view: "not-started" | "live" | "completed" }) {
  const location = [league.city, league.district, league.state].filter(Boolean).join(", ");
  return (
    <section className={styles.header}>
      <div className={styles.leagueLogo}>
        {/* eslint-disable-next-line @next/next/no-img-element -- organizer-uploaded image on our S3 host; plain <img> keeps next/image's remotePatterns closed */}
        {league.logoUrl ? <img src={league.logoUrl} alt="" /> : initials(league.name)}
      </div>
      <div className={styles.headerText}>
        <div className={styles.titleRow}>
          <h1 className={styles.leagueName}>{league.name}</h1>
          {view === "live" && (
            <span className={`${styles.chip} ${styles.chipLive}`}>
              <span className={styles.liveDot} />
              LIVE
            </span>
          )}
          {view === "not-started" && <span className={`${styles.chip} ${styles.chipNotStarted}`}>NOT STARTED</span>}
          {view === "completed" && (
            <span className={`${styles.chip} ${styles.chipDone}`}>
              <Icon name="check" size={14} />
              COMPLETED
            </span>
          )}
        </div>
        <div className={styles.meta}>
          <span className={styles.metaItem}>
            <Icon name="location_on" size={17} className={styles.metaIcon} />
            {location}
          </span>
          <span className={styles.metaItem}>
            <Icon name="event" size={17} className={styles.metaIcon} />
            Starts {formatStartsOn(league.startsOn)}
          </span>
          {league.format && (
            <span className={styles.metaItem}>
              <Icon name="sports_cricket" size={17} className={styles.metaIcon} />
              {league.format}
            </span>
          )}
          {league.groundName && (
            <span className={styles.metaItem}>
              <Icon name="stadium" size={17} className={styles.metaIcon} />
              {league.groundName}
            </span>
          )}
        </div>
      </div>
    </section>
  );
}

function AppBanner() {
  const [dismissed, setDismissed] = useState(false);
  if (!PLAY_STORE_URL || dismissed) return null;
  return (
    <div className={styles.appBanner}>
      <div className={styles.appBannerCard}>
        {/* eslint-disable-next-line @next/next/no-img-element -- tiny static icon */}
        <img src="/crichere-icon.png" alt="" width={36} height={36} style={{ width: 36, height: 36, flex: "none", borderRadius: 10, display: "block" }} />
        <span className={styles.appBannerText}>
          <span className={styles.appBannerTitle}>Watch on the Crichere app</span>
          <span className={styles.appBannerSub}>Faster updates + sold alerts</span>
        </span>
        <a href={PLAY_STORE_URL} target="_blank" rel="noopener noreferrer" className={styles.openPill}>
          Open
        </a>
        <button type="button" aria-label="Dismiss" className={styles.dismiss} onClick={() => setDismissed(true)}>
          <Icon name="close" size={20} />
        </button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- not started

function NotStarted({ league }: { league: League }) {
  const squad = league.auctionSquadMin != null && league.auctionSquadMax != null ? `${league.auctionSquadMin}–${league.auctionSquadMax}` : null;
  const claimed = league.franchisesRequired ? `${league.franchises.length} of ${league.franchisesRequired} claimed` : `${league.franchises.length} claimed`;
  return (
    <div className={styles.twoCol}>
      <section className={styles.noticeCard}>
        <div aria-hidden="true" className={styles.noticeGlow} />
        <span className={styles.goldTile}>
          <Icon name="schedule" size={28} />
        </span>
        <h2 className={styles.noticeTitle}>The auction hasn’t started yet</h2>
        <p className={styles.noticeBody}>
          {league.auctionScheduledAt && (
            <>
              Bidding is scheduled for <b>{formatScheduled(league.auctionScheduledAt)}</b>.{" "}
            </>
          )}
          This page goes live the moment the organizer opens the first lot — keep it open, or come back later.
        </p>
        <div className={styles.rule} />
        <div className={styles.notify}>
          <Icon name="notifications_active" size={24} className={styles.notifyIcon} />
          <div className={styles.notifyText}>
            <span className={styles.notifyTitle}>Get notified in the app</span>
            <span className={styles.notifyCopy}>A push alert when bidding opens, and every time a player is sold.</span>
          </div>
        </div>
        <div className={styles.badges}>
          <StoreBadge kind="play" />
          <StoreBadge kind="appstore" />
        </div>
      </section>
      <aside className={styles.facts} aria-label="Auction facts">
        {league.auctionBasePrice && <Fact label="Base price" value={formatInr(league.auctionBasePrice)} />}
        {league.auctionPurse && <Fact label="Purse per franchise" value={formatInr(league.auctionPurse)} />}
        {squad && <Fact label="Squad size" value={squad} />}
        <Fact label="Players in the pool" value={String(league.players.length)} />
        <span className={styles.factsHeading}>Franchises · {claimed}</span>
        <ul className={styles.franchiseList}>
          {league.franchises.map((franchise, index) => (
            <li key={franchise.id} className={styles.franchiseItem}>
              <FranchiseBadge name={franchise.name} index={index} size={26} fontSize={10} />
              {franchise.name}
            </li>
          ))}
        </ul>
      </aside>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.fact}>
      <span className={styles.factLabel}>{label}</span>
      <span className={styles.mono}>{value}</span>
    </div>
  );
}

// ---------------------------------------------------------------- live

function Live({
  league,
  auction,
  results,
  outcome,
  dimmed,
}: {
  league: League;
  auction: AuctionState;
  results: AuctionResults | null;
  outcome: Outcome | null;
  dimmed: boolean;
}) {
  const order = franchiseOrder(league);
  return (
    <div className={styles.liveWrap}>
      <div className={`${styles.liveGrid} ${dimmed ? styles.dimmed : ""}`}>
        <div className={styles.heroCol}>
          <PlayerCard league={league} auction={auction} outcome={outcome} order={order} />
          <BidTicker league={league} auction={auction} order={order} />
        </div>
        <Standings league={league} auction={auction} results={results} outcome={outcome} order={order} />
      </div>
      {outcome?.sold && <SoldBand outcome={outcome} />}
      {outcome && !outcome.sold && <UnsoldBand outcome={outcome} basePrice={league.auctionBasePrice} />}
    </div>
  );
}

/** Franchise id -> position in the league's list, so each keeps one badge colour everywhere. */
function franchiseOrder(league: League): Map<string, number> {
  return new Map(league.franchises.map((franchise, index) => [franchise.id, index]));
}

function FranchiseBadge({ name, index, size, fontSize }: { name: string | null; index: number | undefined; size: number; fontSize: number }) {
  const known = index !== undefined && name;
  return (
    <span
      aria-hidden="true"
      className={styles.fBadge}
      style={{ width: size, height: size, fontSize, background: known ? franchiseColor(index) : "rgba(255,255,255,.12)" }}
    >
      {name ? initials(name) : "—"}
    </span>
  );
}

function PlayerCard({ league, auction, outcome, order }: { league: League; auction: AuctionState; outcome: Outcome | null; order: Map<string, number> }) {
  const cardRef = useRef<HTMLElement>(null);
  const glowRef = useRef<HTMLSpanElement>(null);
  const leaderRef = useRef<HTMLDivElement>(null);
  const playerId = auction.currentPlayerId;
  const bid = auction.currentBidAmount;
  const leaderId = auction.currentLeadingFranchiseId;

  // Next player: card turns in (rotateY -75deg -> 0, scale .96 -> 1).
  const previousPlayer = useRef(playerId);
  useLayoutEffect(() => {
    if (previousPlayer.current !== playerId && playerId) {
      animate(
        cardRef.current,
        [
          { transform: "perspective(1400px) rotateY(-75deg) scale(.96)", opacity: 0 },
          { transform: "perspective(1400px) rotateY(0deg) scale(1)", opacity: 1 },
        ],
        { duration: 700, easing: springSoft() },
        250,
      );
    }
    previousPlayer.current = playerId;
  }, [playerId]);

  // New bid on the same player: gold glow; leader swap when the leader changed.
  const previousBid = useRef({ playerId, bid, leaderId });
  useLayoutEffect(() => {
    const before = previousBid.current;
    previousBid.current = { playerId, bid, leaderId };
    if (before.playerId !== playerId || before.bid === bid || !bid) return;
    animate(
      glowRef.current,
      [
        { opacity: 0, transform: "scale(.7)" },
        { opacity: 1, transform: "scale(1.05)", offset: 0.25 },
        { opacity: 0, transform: "scale(1.25)" },
      ],
      { duration: 1000, easing: EXPO_OUT },
      0,
    );
    if (before.leaderId !== leaderId) {
      animate(leaderRef.current, [{ transform: "translateY(110%)", opacity: 0 }, { transform: "none", opacity: 1 }], { duration: 520, easing: springSoft() });
    }
  }, [playerId, bid, leaderId]);

  if (!playerId) {
    const pending = auction.playersPending;
    // Design update #4 W3: nothing else can sell -- don't promise a next player.
    if (auction.canAnyoneBid === false) {
      return (
        <article ref={cardRef} aria-label="Bidding closed" className={`${styles.panel} ${styles.playerCard}`}>
          <div className={styles.playerTop}>
            <div className={styles.playerInfo}>
              <div className={styles.lotRow}>
                <span className={styles.lot}>Bidding closed</span>
                {pending != null && <span className={styles.lot}>{pending} left in pool</span>}
              </div>
              <h2 className={styles.playerName}>Waiting for the organizer</h2>
              <p className={styles.betweenText}>No franchise can buy the remaining players. Final results appear here when the auction ends.</p>
            </div>
          </div>
        </article>
      );
    }
    // Design update #4 W2: what just happened and how far along the auction is.
    const total = auction.playersTotal ?? 0;
    const done = pending != null && total > 0 ? Math.max(0, total - pending) : null;
    const last = auction.lastResult;
    return (
      <article ref={cardRef} aria-label="Between lots" className={`${styles.panel} ${styles.playerCard}`}>
        <div className={styles.playerTop}>
          <div className={styles.playerInfo}>
            <div className={styles.lotRow}>
              <span className={styles.hammer}>Between lots</span>
              {pending != null && (
                <span className={styles.lot}>
                  {auction.currentLotNumber != null ? `Lot ${auction.currentLotNumber + 1} · ` : ""}
                  {pending} left in pool
                </span>
              )}
            </div>
            <h2 className={styles.playerName}>Next player coming up</h2>
            {last && (
              <p className={styles.betweenText}>
                Last: {last.playerName ?? "The last player"}{" "}
                {last.sold ? (
                  <>
                    sold to {last.franchiseName ?? "a franchise"}
                    {last.amount != null && (
                      <>
                        {" "}
                        for <span className={styles.betweenAmount}>{formatInr(last.amount)}</span>
                      </>
                    )}
                    .
                  </>
                ) : (
                  "went unsold."
                )}
              </p>
            )}
            {done != null && (
              <>
                <div className={styles.progressTrack} role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={done} aria-label="Lots done">
                  <div className={styles.progressFill} style={{ width: `${(done / total) * 100}%` }} />
                </div>
                <span className={styles.progressText}>
                  {done} of {total} lots done
                </span>
              </>
            )}
          </div>
        </div>
      </article>
    );
  }

  const sold = outcome?.sold === true;
  const unsold = outcome?.sold === false;
  const bidLabel = bid ? (sold ? "Winning bid" : "Current bid") : unsold ? "Closed at base price" : "Opening at base price";
  const amount = bid ?? league.auctionBasePrice;
  const leaderName = auction.currentLeadingFranchiseName;
  const chips = [
    auction.currentPlayerRole ? { label: formatRole(auction.currentPlayerRole), role: true } : null,
    auction.currentPlayerBattingStyle ? { label: formatBatting(auction.currentPlayerBattingStyle), role: false } : null,
    auction.currentPlayerBowlingStyle ? { label: formatBowling(auction.currentPlayerBowlingStyle), role: false } : null,
  ].filter((chip): chip is { label: string; role: boolean } => chip !== null);
  // The open player is still PENDING, so "left" excludes them.
  const left = auction.playersPending != null ? Math.max(0, auction.playersPending - 1) : null;

  return (
    <article ref={cardRef} aria-label="Player under the hammer" className={`${styles.panel} ${styles.playerCard}`}>
      <div className={styles.playerTop}>
        <div className={`${styles.photo} ${unsold ? styles.photoGray : ""}`}>
          {auction.currentPlayerPhotoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- player-uploaded photo on our S3 host
            <img src={auction.currentPlayerPhotoUrl} alt="" />
          ) : (
            <span className={styles.photoInitials}>{initials(auction.currentPlayerName, 2)}</span>
          )}
        </div>
        <div className={styles.playerInfo}>
          <div className={styles.lotRow}>
            <span className={styles.hammer}>Under the hammer</span>
            {auction.currentLotNumber != null && (
              <span className={styles.lot}>
                Lot {auction.currentLotNumber}
                {left !== null ? ` · ${left} left in pool` : ""}
              </span>
            )}
          </div>
          <h2 className={styles.playerName}>{auction.currentPlayerName ?? "Unnamed player"}</h2>
          {chips.length > 0 && (
            <div className={styles.roleChips}>
              {chips.map((chip) => (
                <span key={chip.label} className={chip.role ? styles.roleChip : styles.styleChip}>
                  {chip.label}
                </span>
              ))}
            </div>
          )}
          {league.auctionBasePrice && (
            <div className={styles.base}>
              Base price<span className={styles.baseValue}>{formatInr(league.auctionBasePrice)}</span>
            </div>
          )}
        </div>
      </div>
      <div className={styles.divider} />
      <div className={styles.bidRow}>
        <div className={styles.bidCol}>
          <span className={styles.label}>{bidLabel}</span>
          <div className={styles.bidWrap}>
            <span ref={glowRef} aria-hidden="true" className={styles.bidGlow} />
            <div aria-live="polite" className={styles.bid} style={{ color: bid ? "var(--auction-gold)" : "var(--auction-muted)" }}>
              {amount ? <Odometer amount={Number(amount)} /> : "—"}
            </div>
          </div>
        </div>
        <div className={styles.leaderCol}>
          <span className={styles.label}>Leading franchise</span>
          <div className={styles.leaderSlot}>
            <div ref={leaderRef} className={styles.leader}>
              <FranchiseBadge name={leaderName} index={leaderId ? order.get(leaderId) : undefined} size={32} fontSize={11} />
              <span className={styles.leaderName}>{leaderName ?? "No bids yet"}</span>
            </div>
          </div>
        </div>
      </div>
    </article>
  );
}

/** Per-digit columns translated to the digit, keyed from the right so a new leading digit slides in cleanly. */
function Odometer({ amount }: { amount: number }) {
  const reduced = useReducedMotion();
  const text = formatInr(amount);
  const chars = [...text];
  return (
    <span aria-label={text} className={styles.odometer}>
      {chars.map((char, i) => {
        const fromRight = chars.length - 1 - i;
        if (!/\d/.test(char)) {
          return (
            <span key={`s${fromRight}${char}`} aria-hidden="true" className={styles.odoChar}>
              {char}
            </span>
          );
        }
        return (
          <span key={`d${fromRight}`} aria-hidden="true" className={styles.odoDigit}>
            <span className={styles.odoColumn} style={{ transform: `translateY(${-Number(char) * 10}%)`, transition: reduced ? "none" : undefined }}>
              {DIGITS.map((digit) => (
                <span key={digit}>{digit}</span>
              ))}
            </span>
          </span>
        );
      })}
    </span>
  );
}

const DIGITS = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"];

function BidTicker({ league, auction, order }: { league: League; auction: AuctionState; order: Map<string, number> }) {
  const listRef = useRef<HTMLOListElement>(null);
  const now = useNow(1000);
  const rows = auction.recentBids.slice(0, 6);
  const topKey = rows[0] ? `${rows[0].franchiseId}-${rows[0].placedAt}` : null;
  const count = auction.recentBids.length;

  // New bid: the list slides down one row height while the new row fades in.
  const previous = useRef({ topKey, playerId: auction.currentPlayerId });
  useLayoutEffect(() => {
    const before = previous.current;
    previous.current = { topKey, playerId: auction.currentPlayerId };
    if (!topKey || before.topKey === topKey || before.playerId !== auction.currentPlayerId) return;
    const list = listRef.current;
    const first = list?.firstElementChild as HTMLElement | null;
    if (!list || !first) return;
    if (before.topKey && !prefersReducedMotion()) {
      animate(list, [{ transform: `translateY(${-(first.getBoundingClientRect().height + 6)}px)` }, { transform: "none" }], { duration: 520, easing: springSoft() });
    }
    animate(first, [{ opacity: 0 }, { opacity: 1 }], { duration: 380, easing: "ease-out" });
  }, [topKey, auction.currentPlayerId]);

  return (
    <section aria-label="Recent bids" className={`${styles.panel} ${styles.ticker}`}>
      <div className={styles.panelHead}>
        <span className={styles.panelTitle}>Recent bids</span>
        <span className={styles.panelMeta}>{count ? `${count} bid${count === 1 ? "" : "s"} on this player` : "Clears for each new player"}</span>
      </div>
      {rows.length === 0 && (
        <div className={styles.tickerEmpty}>
          No bids on this player yet{league.auctionBasePrice ? <> — bidding opens at <span>{formatInr(league.auctionBasePrice)}</span></> : null}
        </div>
      )}
      <div className={styles.tickerClip}>
        <ol ref={listRef} className={styles.tickerList}>
          {rows.map((bid, index) => (
            <li key={`${bid.franchiseId}-${bid.placedAt}`} className={`${styles.tickerRow} ${index === 0 ? styles.tickerTop : ""}`}>
              <FranchiseBadge name={bid.franchiseName} index={order.get(bid.franchiseId)} size={28} fontSize={10} />
              <span className={styles.tickerName}>{bid.franchiseName ?? "Unknown franchise"}</span>
              <span className={styles.tickerAmount}>{formatInr(bid.amount)}</span>
              <span className={styles.tickerAgo}>{formatAgo(bid.placedAt, now)}</span>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function Standings({
  league,
  auction,
  results,
  outcome,
  order,
}: {
  league: League;
  auction: AuctionState;
  results: AuctionResults | null;
  outcome: Outcome | null;
  order: Map<string, number>;
}) {
  const squadMin = league.auctionSquadMin;
  const squadMax = league.auctionSquadMax;
  const purse = league.auctionPurse ? Number(league.auctionPurse) : null;
  const leadingId = outcome?.sold === false ? null : auction.currentLeadingFranchiseId;
  return (
    <aside aria-label="Franchise standings" className={`${styles.panel} ${styles.standings}`}>
      <div className={`${styles.panelHead} ${styles.standingsHead}`}>
        <span className={styles.panelTitle}>Franchise standings</span>
        {squadMin != null && squadMax != null && (
          <span className={styles.panelMeta} style={{ fontSize: 11 }}>
            Squad {squadMin}–{squadMax}
          </span>
        )}
      </div>
      {results?.franchises.map((franchise) => (
        <StandingRow
          key={franchise.franchiseId}
          franchise={franchise}
          index={order.get(franchise.franchiseId)}
          leading={franchise.franchiseId === leadingId}
          won={outcome?.sold === true && franchise.franchiseId === leadingId}
          squadMin={squadMin}
          squadMax={squadMax}
          purse={purse}
        />
      ))}
    </aside>
  );
}

function StandingRow({
  franchise,
  index,
  leading,
  won,
  squadMin,
  squadMax,
  purse,
}: {
  franchise: FranchiseResult;
  index: number | undefined;
  leading: boolean;
  won: boolean;
  squadMin: number | null;
  squadMax: number | null;
  purse: number | null;
}) {
  const squad = franchise.playersWon.length;
  const remaining = franchise.purseRemaining !== null ? Number(franchise.purseRemaining) : null;
  const pct = remaining !== null && purse ? Math.max(0, Math.min(1, remaining / purse)) : null;
  // U5 W5: never a negative number -- the label changes and the empty track turns alert.
  const over = remaining !== null && remaining < 0;
  return (
    <div className={`${styles.standing} ${leading ? styles.standingLead : ""}`}>
      <div className={styles.standingTop}>
        <FranchiseBadge name={franchise.franchiseName} index={index} size={28} fontSize={10} />
        <span className={styles.standingName}>{franchise.franchiseName}</span>
        {leading && <span className={styles.tag}>{won ? "WON" : "LEADING"}</span>}
        <span className={styles.squad}>
          {squad}
          <span> plyrs</span>
        </span>
      </div>
      {squadMax != null && (
        <div className={styles.pips} style={{ gridTemplateColumns: `repeat(${squadMax}, 1fr)` }} aria-hidden="true">
          {Array.from({ length: squadMax }, (_, i) => {
            const inMin = squadMin != null && i < squadMin;
            const background = i < squad ? (inMin ? "#D7EBD2" : "#9FAE9A") : inMin ? "rgba(215,235,210,.16)" : "rgba(255,255,255,.06)";
            return <span key={i} className={styles.pip} style={{ background }} />;
          })}
        </div>
      )}
      {remaining !== null && (
        <>
          {over ? (
            <div className={`${styles.purseLine} ${styles.purseLineOver}`}>
              <span aria-hidden="true">Over purse</span>
              <span className={styles.purseValue} aria-hidden="true">
                {formatInr(-remaining)}
              </span>
              <span className={styles.srOnly}>Over purse by {formatInr(-remaining)}</span>
            </div>
          ) : (
            <div className={styles.purseLine}>
              <span className={styles.factLabel}>Purse left</span>
              <span className={styles.purseValue}>{formatInr(remaining)}</span>
            </div>
          )}
          {pct !== null && (
            <div className={`${styles.purseTrack} ${over ? styles.purseTrackOver : ""}`} aria-hidden="true">
              <div className={styles.purseFill} style={{ transform: `scaleX(${pct.toFixed(3)})`, background: leading ? "var(--auction-gold)" : "var(--outline)" }} />
            </div>
          )}
        </>
      )}
    </div>
  );
}

function SoldBand({ outcome }: { outcome: Outcome }) {
  const scrimRef = useRef<HTMLDivElement>(null);
  const bandRef = useRef<HTMLDivElement>(null);
  const stampRef = useRef<HTMLDivElement>(null);
  const gavelRef = useRef<HTMLSpanElement>(null);
  const sparkRef = useRef<HTMLDivElement>(null);

  // scrim 300ms -> band scale(.3,.6)->1 440ms -> stamp 560ms bounce -> gavel swing 620ms -> 30 gold sparks.
  useLayoutEffect(() => {
    animate(scrimRef.current, [{ opacity: 0 }, { opacity: 1 }], { duration: 300 }, 300);
    if (prefersReducedMotion()) {
      animate(bandRef.current, [], {}, 300);
      return;
    }
    animate(bandRef.current, [{ transform: "scale(.3, .6)", opacity: 0 }, { transform: "none", opacity: 1 }], { duration: 440, easing: springSoft() });
    animate(
      stampRef.current,
      [
        { transform: "scale(2.4) rotate(-12deg)", opacity: 0 },
        { transform: "scale(1) rotate(-4deg)", opacity: 1 },
      ],
      { duration: 560, delay: 140, easing: bounce(), fill: "backwards" },
    );
    animate(
      gavelRef.current,
      [{ transform: "rotate(-55deg)" }, { transform: "rotate(22deg)", offset: 0.55 }, { transform: "rotate(0deg)" }],
      { duration: 620, delay: 140, easing: "cubic-bezier(.5,0,.2,1)", fill: "backwards" },
    );
    const timer = setTimeout(() => burst(sparkRef.current), 380);
    return () => clearTimeout(timer);
  }, [outcome.key]);

  return (
    <>
      <div ref={scrimRef} aria-hidden="true" className={styles.scrim} />
      <div className={styles.bandHost}>
        <div ref={bandRef} role="status" className={styles.soldBand}>
          <div ref={stampRef} className={styles.stamp}>
            <span ref={gavelRef} className={styles.gavel} style={{ display: "inline-block" }}>
              <Icon name="gavel" size={72} style={{ width: "100%", height: "100%" }} />
            </span>
            <span className={styles.soldWord}>SOLD</span>
          </div>
          <div className={styles.soldText}>
            <span className={styles.soldPlayer}>{outcome.playerName ?? "Unnamed player"}</span>
            {outcome.franchiseName && <span className={styles.soldTo}>to {outcome.franchiseName}</span>}
            {outcome.amount && <span className={styles.soldPrice}>{formatInr(outcome.amount)}</span>}
          </div>
        </div>
        <div ref={sparkRef} aria-hidden="true" className={styles.sparks} />
      </div>
    </>
  );
}

/** 30 gold sparks flung from the band's centre, 0.8-1.3s, then removed. */
function burst(host: HTMLElement | null) {
  if (!host || typeof host.animate !== "function") return;
  const colors = ["#F2B544", "#FFE3A3", "#F5F6F1", "#F2B544"];
  for (let i = 0; i < 30; i++) {
    const spark = document.createElement("span");
    const angle = Math.random() * Math.PI * 2;
    const distance = 120 + Math.random() * 260;
    const size = 4 + Math.random() * 6;
    spark.style.cssText = `position:absolute;left:0;top:0;width:${size}px;height:${i % 3 ? size : size * 2.6}px;background:${colors[i % 4]};border-radius:${i % 2 ? "50%" : "2px"};pointer-events:none;will-change:transform,opacity`;
    host.appendChild(spark);
    const animation = spark.animate(
      [
        { transform: "translate(-50%,-50%) scale(1)", opacity: 1 },
        {
          transform: `translate(calc(-50% + ${Math.cos(angle) * distance}px), calc(-50% + ${Math.sin(angle) * distance * 0.55}px)) rotate(${Math.random() * 540}deg) scale(.3)`,
          opacity: 0,
        },
      ],
      { duration: 800 + Math.random() * 500, easing: "cubic-bezier(.15,.7,.3,1)" },
    );
    animation.onfinish = () => spark.remove();
  }
}

function UnsoldBand({ outcome, basePrice }: { outcome: Outcome; basePrice: string | null }) {
  const bandRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    animate(bandRef.current, [{ opacity: 0, transform: "translateY(8px)" }, { opacity: 1, transform: "none" }], { duration: 400, easing: "ease-out" }, 400);
  }, [outcome.key]);
  return (
    <div ref={bandRef} role="status" className={styles.unsoldBand}>
      <span className={styles.unsoldWord}>UNSOLD</span>
      <span className={styles.unsoldText}>
        <span className={styles.unsoldTitle}>{outcome.playerName ?? "This player"} returns to the pool</span>
        {basePrice && (
          <span className={styles.unsoldNote}>
            No bids at the base price of <span>{formatInr(basePrice)}</span>
          </span>
        )}
      </span>
    </div>
  );
}

// ---------------------------------------------------------------- completed

function Completed({ league, auction, results }: { league: League; auction: AuctionState; results: AuctionResults | null }) {
  if (!results) {
    return <p className={styles.subtle}>Loading final results…</p>;
  }
  const order = franchiseOrder(league);
  const sold = results.franchises.reduce((total, franchise) => total + franchise.playersWon.length, 0);
  const pool = auction.playersTotal || league.players.length;
  const spent = results.franchises.reduce((total, franchise) => total + Number(franchise.purseSpent), 0);
  return (
    <>
      <section className={styles.summary}>
        <span className={styles.summaryLead}>
          <span className={styles.summaryTile}>
            <Icon name="celebration" size={26} />
          </span>
          <span className={styles.stack}>
            <h2 className={styles.summaryTitle}>Auction complete</h2>
            <span className={styles.subtle}>Squads are final</span>
          </span>
        </span>
        <Summary value={String(sold)} label="players sold" />
        <Summary value={String(Math.max(0, pool - sold))} label="unsold" />
        <Summary value={formatInr(spent)} label="total spent" gold />
      </section>
      <div className={styles.resultsGrid}>
        {results.franchises.map((franchise) => {
          const players = [...franchise.playersWon].sort((a, b) => Number(b.soldPrice) - Number(a.soldPrice));
          return (
            <article key={franchise.franchiseId} className={`${styles.resultCard} ${franchise.belowSquadMin ? styles.resultBelow : ""}`}>
              <div className={styles.resultHead}>
                <FranchiseBadge name={franchise.franchiseName} index={order.get(franchise.franchiseId)} size={40} fontSize={12} />
                <span className={styles.stack} style={{ flex: 1, minWidth: 0 }}>
                  <h3 className={styles.resultName}>{franchise.franchiseName}</h3>
                  <span className={styles.summaryLabel}>
                    {players.length} player{players.length === 1 ? "" : "s"}
                  </span>
                </span>
              </div>
              <div className={styles.tiles}>
                <span className={styles.tile}>
                  <span className={styles.tileLabel}>Spent</span>
                  <span className={styles.tileValue} style={{ color: "var(--auction-gold)" }}>
                    {formatInr(franchise.purseSpent)}
                  </span>
                </span>
                {franchise.purseRemaining !== null &&
                  (Number(franchise.purseRemaining) < 0 ? (
                    // U5 W6: "Over purse" tile in alert; the card border stays (.resultBelow is the squad warning).
                    <span className={`${styles.tile} ${styles.tileOver}`}>
                      <span className={styles.tileLabel}>Over purse</span>
                      <span className={styles.tileValue}>{formatInr(-Number(franchise.purseRemaining))}</span>
                    </span>
                  ) : (
                    <span className={styles.tile}>
                      <span className={styles.tileLabel}>Purse left</span>
                      <span className={styles.tileValue}>{formatInr(franchise.purseRemaining)}</span>
                    </span>
                  ))}
              </div>
              {franchise.belowSquadMin && (
                <span className={styles.warning}>
                  <Icon name="warning" size={18} />
                  Below squad minimum{league.auctionSquadMin != null ? ` · ${players.length} of ${league.auctionSquadMin}` : ""}
                </span>
              )}
              {players.length > 0 ? (
                <ol className={styles.wonList}>
                  {players.map((player) => (
                    <li key={player.playerId} className={styles.wonRow}>
                      <span className={styles.wonName}>{player.playerName ?? "Unnamed player"}</span>
                      <span className={styles.wonRole}>{player.playingRole ? formatRoleShort(player.playingRole) : ""}</span>
                      <span className={styles.wonPrice}>{formatInr(player.soldPrice)}</span>
                    </li>
                  ))}
                </ol>
              ) : (
                <span className={styles.subtle}>No players won.</span>
              )}
            </article>
          );
        })}
      </div>
    </>
  );
}

function Summary({ value, label, gold }: { value: string; label: string; gold?: boolean }) {
  return (
    <span className={styles.stack}>
      <span className={styles.summaryValue} style={gold ? { color: "var(--auction-gold)" } : undefined}>
        {value}
      </span>
      <span className={styles.summaryLabel}>{label}</span>
    </span>
  );
}

// ---------------------------------------------------------------- shared

/** A clock that ticks every [intervalMs] -- drives "14s ago" style labels. */
function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
