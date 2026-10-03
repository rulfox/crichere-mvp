import Link from "next/link";
import { HeroPhone } from "@/components/landing/HeroPhone";
import { LandingMotion } from "@/components/landing/LandingMotion";
import styles from "@/components/landing/Landing.module.css";
import { Icon } from "@/components/ui/Icon";
import { Logo } from "@/components/ui/Logo";
import { StoreBadge } from "@/components/ui/StoreBadge";
import { fetchLiveNow } from "@/lib/api";

/** Re-checked every 15s so "Watch live" follows whichever auction is running (docs/PHASE11.md D5). */
export const revalidate = 15;

/** 18 twinkling floodlight sparks, positioned exactly as the design's generator places them. */
const SPARKS = Array.from({ length: 18 }, (_, i) => ({
  left: `${(i * 37) % 100}%`,
  top: `${(i * 53) % 70}%`,
  size: 2 + (i % 3),
  duration: `${(3.2 + (i % 5) * 0.7).toFixed(1)}s`,
  delay: `${((i * 0.43) % 4).toFixed(2)}s`,
}));

/** Placeholder marketing numbers from the design (docs/PHASE11.md D4) -- replace with real ones before launch. */
const STATS = [
  { value: 1240, label: "leagues organized", delay: 0 },
  { value: 38600, label: "players registered", delay: 100 },
  { value: 964000, label: "bids placed", delay: 200 },
];

export default async function Landing() {
  const live = await fetchLiveNow();
  const liveHref = live ? `/leagues/${live.leagueId}` : null;

  return (
    <div id="landing" className={styles.root}>
      <LandingMotion rootId="landing" readyClass={styles.motionReady} stepOnClass={styles.stepOn} />

      <header className={styles.hero}>
        <div aria-hidden="true" className={styles.heroBackdrop}>
          <div className={styles.floodA} />
          <div className={styles.floodB} />
          <div className={styles.grass} />
          {SPARKS.map((spark, i) => (
            <span
              key={i}
              className={styles.spark}
              style={{ left: spark.left, top: spark.top, width: spark.size, height: spark.size, animationDuration: spark.duration, animationDelay: spark.delay }}
            />
          ))}
        </div>

        <nav className={styles.nav} aria-label="Main">
          <Logo size={34} fontSize={22} href="/" className={styles.navLogo} />
          <div className={styles.navLinks}>
            <a href="#features" className={styles.navLink}>
              Features
            </a>
            <a href="#how" className={styles.navLink}>
              How auctions work
            </a>
            {liveHref && (
              <Link href={liveHref} className={styles.navLink}>
                <span className={styles.liveDot} />
                Watch live
              </Link>
            )}
          </div>
          <a href="#download" className={styles.getApp}>
            <Icon name="download" size={20} />
            Get the app
          </a>
        </nav>

        <div className={styles.heroBody}>
          <div className={styles.heroCopy}>
            <span className={styles.eyebrow}>
              <span className={styles.liveDot} />
              Local cricket, live auctions
            </span>
            <h1 className={styles.heroTitle}>
              <span>Build.</span>
              <span className={styles.gold}>Auction.</span>
              <span>Compete.</span>
            </h1>
            <p className={styles.heroSub}>
              The ultimate platform to organize and manage cricket leagues. Run your town’s league from sign-ups to final squads — live player auctions
              included.
            </p>
            <div className={styles.badges}>
              <StoreBadge kind="play" />
              <StoreBadge kind="appstore" />
            </div>
            {liveHref && (
              <Link href={liveHref} className={styles.watchLink}>
                <span className={styles.playRing}>
                  <Icon name="play_arrow" size={20} />
                </span>
                Watch a live auction
                <Icon name="arrow_forward" size={20} />
              </Link>
            )}
          </div>

          <div className={styles.deviceCol}>
            <div data-depth="0.05" className={styles.device}>
              <HeroPhone />
              <div data-depth="0.08" className={`${styles.floatCard} ${styles.floatLive} ${styles.roomyOnly}`}>
                <span className={styles.floatIcon}>
                  <Icon name="notifications_active" size={18} />
                </span>
                <span className={styles.floatText}>
                  <span className={styles.floatTitle}>Auction is live</span>
                  <span className={styles.floatBody}>Spartanz Premier League · 30 players up for bids</span>
                </span>
              </div>
              <div data-depth="0.16" className={`${styles.floatCard} ${styles.floatBids} ${styles.roomyOnly}`}>
                <span className={styles.floatLabel}>Bids placed tonight</span>
                <span className={styles.floatValue}>412</span>
              </div>
            </div>
          </div>
        </div>
      </header>

      <section id="features" className={`${styles.section} ${styles.features}`}>
        <div data-reveal="" className={styles.sectionHead}>
          <span className={styles.kicker}>Everything in one app</span>
          <h2 className={styles.h2}>From the first sign-up to the last squad.</h2>
          <p className={styles.lede}>Built for maidan, gully and club cricket — the leagues that run on WhatsApp groups and goodwill.</p>
        </div>

        <div className={styles.featureGrid}>
          <article data-reveal="" data-delay="0" className={styles.card}>
            <span className={styles.iconTile}>
              <Icon name="travel_explore" />
            </span>
            <h3 className={styles.cardTitle}>Discover leagues near you</h3>
            <p className={styles.cardCopy}>Filter by state, district and city, or tap “Nearest to me” to see what’s running this weekend.</p>
            <div className={styles.vignette}>
              <div className={styles.chips}>
                <span className={styles.chipSolid}>
                  <Icon name="near_me" size={15} />
                  Nearest to me
                </span>
                <span className={styles.chipOutline}>Kerala › Alappuzha</span>
              </div>
              <div className={styles.miniRow}>
                <span className={styles.miniName}>Spartanz Premier League</span>
                <span className={styles.miniMeta}>2.4 km</span>
              </div>
              <div className={styles.miniRow}>
                <span className={styles.miniName}>Mannancherry T10 Cup</span>
                <span className={styles.miniMeta}>6.1 km</span>
              </div>
            </div>
          </article>

          <article data-reveal="" data-delay="80" className={styles.card}>
            <span className={styles.iconTile}>
              <Icon name="add_location_alt" />
            </span>
            <h3 className={styles.cardTitle}>Create a league in minutes</h3>
            <p className={styles.cardCopy}>Pick the format, drop a pin on your ground, set capacity, entry fees and awards. Done.</p>
            <div className={`${styles.vignette} ${styles.vignetteRows}`}>
              <div className={styles.factRow}>
                <span className={styles.factLabel}>Format</span>
                <span className={styles.factValue}>T10</span>
              </div>
              <div className={styles.factRow}>
                <span className={styles.factLabel}>Ground</span>
                <span className={styles.factValue}>
                  <Icon name="location_on" size={15} className={styles.green} />
                  Udhaya Ground
                </span>
              </div>
              <div className={styles.factRow}>
                <span className={styles.factLabel}>Capacity</span>
                <span className={styles.factMono}>6 teams · 30 players</span>
              </div>
              <div className={styles.factRow}>
                <span className={styles.factLabel}>Entry fee</span>
                <span className={styles.factMono}>₹300 / ₹5,000</span>
              </div>
            </div>
          </article>

          <article data-reveal="" data-delay="160" className={styles.card}>
            <span className={styles.iconTile}>
              <Icon name="how_to_reg" />
            </span>
            <h3 className={styles.cardTitle}>Join as a player, or claim a franchise</h3>
            <p className={styles.cardCopy}>Pay the organizer directly over UPI and upload the screenshot as proof. No gateway, no cut.</p>
            <div className={styles.vignette}>
              <div className={styles.miniRow}>
                <Icon name="sports_cricket" size={20} className={styles.green} />
                <span className={styles.miniName}>Claim Spartanz</span>
                <span className={styles.miniMono}>₹5,000</span>
              </div>
              <div className={styles.proofNote}>
                <Icon name="receipt_long" size={16} />
                UPI screenshot uploaded · awaiting organizer
              </div>
            </div>
          </article>

          <article data-reveal="" className={styles.liveCard}>
            <div aria-hidden="true" className={styles.liveCardGlow} />
            <div className={styles.liveCardCopy}>
              <span className={styles.goldTile}>
                <Icon name="gavel" />
              </span>
              <h3 className={styles.liveCardTitle}>
                The live auction. <span className={styles.gold}>The main event.</span>
              </h3>
              <p className={styles.liveCardText}>
                Real-time bids, sold and unsold rounds, and purse and squad tracking for every franchise — on every phone in the league, at the same
                moment.
              </p>
              {liveHref && (
                <Link href={liveHref} className={styles.seeRunning}>
                  See one running
                  <Icon name="arrow_forward" size={18} />
                </Link>
              )}
            </div>
            <div className={styles.liveCardDemo} aria-hidden="true">
              <div className={styles.leadBox}>
                <span className={styles.leadText}>
                  <span className={styles.floatLabel}>Victory CC lead</span>
                  <span className={styles.leadAmount}>₹1,20,000</span>
                </span>
                <span className={styles.leadPlayer}>Deepak Boche</span>
              </div>
              <div className={styles.purseGrid}>
                {[
                  ["Spartanz", "₹3,10,000", 0.62, "var(--outline)"],
                  ["Victory CC", "₹3,60,000", 0.72, "var(--auction-gold)"],
                  ["Ashes", "₹4,20,000", 0.84, "var(--outline)"],
                ].map(([name, amount, pct, color]) => (
                  <div key={name as string} className={styles.purseTile}>
                    <span className={styles.purseName}>{name}</span>
                    <span className={styles.purseAmount}>{amount}</span>
                    <span className={styles.track}>
                      <span className={styles.trackFill} style={{ transform: `scaleX(${pct})`, background: color as string }} />
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </article>

          <article data-reveal="" data-delay="0" className={styles.card}>
            <span className={styles.iconTile}>
              <Icon name="group_add" />
            </span>
            <h3 className={styles.cardTitle}>Bring in co-organizers</h3>
            <p className={styles.cardCopy}>Share the load. Co-organizers can approve fee proofs, manage players and run the auction desk.</p>
            <div className={`${styles.vignette} ${styles.vignetteInline}`} style={{ gap: 12 }}>
              <div className={styles.avatars}>
                <span className={styles.avatar} style={{ background: "oklch(0.76 0.11 25)" }}>
                  RN
                </span>
                <span className={styles.avatar} style={{ background: "oklch(0.76 0.11 195)" }}>
                  AK
                </span>
                <span className={styles.avatar} style={{ background: "oklch(0.76 0.11 300)" }}>
                  SJ
                </span>
              </div>
              <span className={styles.muted13}>Rahul + 2 co-organizers</span>
            </div>
          </article>

          <article data-reveal="" data-delay="80" className={styles.card}>
            <span className={styles.iconTile}>
              <Icon name="notifications_active" />
            </span>
            <h3 className={styles.cardTitle}>Never miss a lot</h3>
            <p className={styles.cardCopy}>Push alerts when the auction starts, when a player is sold or goes unsold, and when your fee is approved.</p>
            <div className={styles.vignette}>
              <div className={styles.notice}>
                <Icon name="celebration" size={18} className={styles.green} />
                <span className={styles.noticeText}>
                  <b>Aswin Sudarsanan</b> sold to Victory CC for <span className={styles.factMono}>₹1,20,000</span>
                </span>
              </div>
              <div className={`${styles.notice} ${styles.noticeFaded}`}>
                <Icon name="gavel" size={18} style={{ color: "var(--ink-muted)" }} />
                <span className={styles.noticeText}>Auction started · Spartanz Premier League</span>
              </div>
            </div>
          </article>

          <article data-reveal="" data-delay="160" className={styles.card}>
            <span className={styles.iconTile}>
              <Icon name="share" />
            </span>
            <h3 className={styles.cardTitle}>Watch on the web, no app</h3>
            <p className={styles.cardCopy}>Drop the watch link in the family WhatsApp group. Anyone can follow the auction live in a browser.</p>
            <div className={`${styles.vignette} ${styles.vignetteInline}`} style={{ gap: 10 }}>
              <span className={styles.linkPill}>
                <Icon name="link" size={16} style={{ color: "var(--ink-muted)" }} />
                crichere.com/leagues/spl-26
              </span>
              {liveHref && (
                <Link href={liveHref} className={styles.openPill}>
                  Open
                </Link>
              )}
            </div>
          </article>
        </div>
      </section>

      <section id="how" data-how="" className={styles.how}>
        <div aria-hidden="true" className={styles.howGlow} />
        <div className={`${styles.section} ${styles.howInner}`}>
          <div data-reveal="" className={styles.sectionHead}>
            <span className={`${styles.kicker} ${styles.kickerGold}`}>How an auction works</span>
            <h2 className={styles.h2}>Four steps from player pool to locked squads.</h2>
          </div>
          <div className={styles.steps}>
            <Step index={0} icon="tune" title="Set the rules" copy="The organizer fixes a base price, each franchise’s purse and the squad size.">
              <div className={styles.ruleChips}>
                <span>Base ₹20,000</span>
                <span>Purse ₹5,00,000</span>
                <span>Squad 4–6</span>
              </div>
            </Step>
            <Step index={1} icon="gavel" title="Under the hammer" copy="Players come up one at a time — photo, role, batting and bowling style, base price." />
            <Step index={2} icon="trending_up" title="Franchises bid live" copy="Owners raise the bid in real time. The leader glows gold; purses update for everyone.">
              <span className={styles.stepAmount}>₹1,20,000</span>
            </Step>
            <Step index={3} icon="military_tech" title="Squads locked" copy="Sold or unsold, every result lands instantly. When the last lot closes, squads are final." />
          </div>
        </div>
      </section>

      <section className={`${styles.section} ${styles.stats}`} aria-label="Crichere in numbers">
        <div className={styles.statGrid}>
          {STATS.map((stat) => (
            <div key={stat.label} data-reveal="" data-delay={stat.delay} className={styles.stat}>
              <span data-count={stat.value} className={styles.statValue}>
                {stat.value.toLocaleString("en-IN")}
              </span>
              <span className={styles.statLabel}>{stat.label}</span>
            </div>
          ))}
        </div>
      </section>

      <section id="download" className={`${styles.section} ${styles.download}`}>
        <div data-reveal="" className={styles.downloadCard}>
          <div aria-hidden="true" className={styles.downloadGlow} />
          <div className={styles.downloadCopy}>
            <h2 className={styles.h2}>Your league is one download away.</h2>
            <p className={styles.downloadText}>Free for players, owners and fans. Android today — iPhone is on the way.</p>
            <div className={styles.badges}>
              <StoreBadge kind="play" />
              <StoreBadge kind="appstore" />
            </div>
          </div>
          <div className={styles.wideOnly}>
            <StoreBadge kind="qr" qrSrc={null} />
          </div>
        </div>
      </section>

      <footer className={styles.footer}>
        <div className={`${styles.section} ${styles.footerTop}`}>
          <div className={styles.footerBrand}>
            <Logo size={30} fontSize={20} />
            <span className={styles.footerTagline}>Build. Auction. Compete. Made for local cricket across India.</span>
          </div>
          <nav aria-label="Footer" className={styles.footerNav}>
            <a href="#">About</a>
            <a href="mailto:hello@crichere.app">Contact</a>
            <a href="#">Privacy</a>
            <a href="#">Terms</a>
          </nav>
        </div>
        <div className={`${styles.section} ${styles.copyright}`}>© 2026 Crichere</div>
      </footer>
    </div>
  );
}

function Step({
  index,
  icon,
  title,
  copy,
  children,
}: {
  index: number;
  icon: "tune" | "gavel" | "trending_up" | "military_tech";
  title: string;
  copy: string;
  children?: React.ReactNode;
}) {
  return (
    <div data-step={index} className={styles.step}>
      <div className={styles.stepTrack}>
        <div className={styles.stepBar} />
      </div>
      <span className={styles.stepNo}>{String(index + 1).padStart(2, "0")}</span>
      <span className={styles.stepTile}>
        <Icon name={icon} size={28} />
      </span>
      <h3 className={styles.stepTitle}>{title}</h3>
      <p className={styles.stepCopy}>{copy}</p>
      {children}
    </div>
  );
}
