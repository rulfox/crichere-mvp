"use client";

import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { formatInr } from "@/lib/auction";
import { animate, bounce, EXPO_OUT, prefersReducedMotion, springSoft } from "@/lib/motion";
import styles from "./HeroPhone.module.css";

/**
 * The hero phone's looping demo auction (sample data from the design -- marketing illustration,
 * not live data): a bid every 1.5s counts up with a gold glow, the leading franchise swaps in,
 * then a SOLD stamp lands and the loop restarts after 2.8s.
 */
const FRANCHISES = {
  ps: { name: "Spartanz", short: "SPZ", color: "oklch(0.76 0.11 25)" },
  tt: { name: "Victory CC", short: "VCC", color: "oklch(0.76 0.11 250)" },
  cc: { name: "Ashes Komalapuram", short: "AK", color: "oklch(0.76 0.11 300)" },
} as const;

type FranchiseId = keyof typeof FRANCHISES;

const STEPS: [FranchiseId, number][] = [
  ["ps", 20000],
  ["tt", 45000],
  ["ps", 70000],
  ["cc", 95000],
  ["tt", 120000],
];

const PURSES: [FranchiseId, number][] = [
  ["ps", 310000],
  ["tt", 360000],
  ["cc", 420000],
];

export function HeroPhone() {
  const [step, setStep] = useState(0);
  const [sold, setSold] = useState(false);
  const bidRef = useRef<HTMLSpanElement>(null);
  const glowRef = useRef<HTMLSpanElement>(null);
  const leaderRef = useRef<HTMLDivElement>(null);
  const stampRef = useRef<HTMLDivElement>(null);
  const shownValue = useRef(STEPS[0][1]);

  // The loop: 5 bids 1.5s apart, SOLD for 2.8s, repeat.
  useEffect(() => {
    let index = 0;
    let timer: ReturnType<typeof setTimeout>;
    const tick = () => {
      if (index < STEPS.length) {
        setSold(false);
        setStep(index);
        index += 1;
        timer = setTimeout(tick, 1500);
      } else if (index === STEPS.length) {
        setSold(true);
        index += 1;
        timer = setTimeout(tick, 2800);
      } else {
        index = 0;
        tick();
      }
    };
    tick();
    return () => clearTimeout(timer);
  }, []);

  // Bid count-up (700ms cubic-out, rounded to ₹500) with the gold glow (1000ms expo-out).
  useEffect(() => {
    const el = bidRef.current;
    const target = STEPS[step][1];
    const from = shownValue.current;
    shownValue.current = target;
    if (!el) return;
    if (prefersReducedMotion() || from === target) {
      el.textContent = formatInr(target);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const frame = (now: number) => {
      const k = Math.min(1, (now - start) / 700);
      const eased = 1 - Math.pow(1 - k, 3);
      el.textContent = formatInr(Math.round((from + (target - from) * eased) / 500) * 500);
      if (k < 1) raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
    if (target > from) {
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
    }
    return () => cancelAnimationFrame(raf);
  }, [step]);

  // Leader swap whenever the leading franchise changes.
  const previousLeader = useRef<FranchiseId | null>(null);
  useEffect(() => {
    const leader = STEPS[step][0];
    if (previousLeader.current !== null && previousLeader.current !== leader) {
      animate(leaderRef.current, [{ transform: "translateY(110%)", opacity: 0 }, { transform: "none", opacity: 1 }], { duration: 520, easing: springSoft() });
    }
    previousLeader.current = leader;
  }, [step]);

  // SOLD stamp: scale 2.4 -> 1, rotate -14deg -> -8deg, bounce. Reduced motion: 250ms fade.
  useEffect(() => {
    if (sold) {
      animate(
        stampRef.current,
        [
          { transform: "scale(2.4) rotate(-14deg)", opacity: 0 },
          { transform: "scale(1) rotate(-8deg)", opacity: 1 },
        ],
        { duration: 560, easing: bounce() },
        250,
      );
    }
  }, [sold]);

  const [leaderId, amount] = STEPS[step];
  const leader = FRANCHISES[leaderId];

  return (
    <div className={styles.frame}>
      <div className={styles.screen} aria-label="Demo of a live Crichere auction" role="img">
        <div className={styles.island} />
        <div className={styles.statusBar}>
          <span>9:41</span>
          <span className={styles.statusIcons}>
            <Icon name="signal_cellular_alt" size={15} />
            <Icon name="wifi" size={15} />
            <Icon name="battery_full" size={15} />
          </span>
        </div>
        <div className={styles.appBar}>
          <Icon name="arrow_back" size={20} className={styles.back} />
          <span className={styles.appTitle}>Spartanz Premier League</span>
          <span className={styles.liveChip}>
            <span className={styles.liveDot} />
            LIVE
          </span>
        </div>
        <div className={styles.playerCard}>
          <div className={styles.photo}>photo</div>
          <div className={styles.playerText}>
            <span className={styles.hammer}>Under the hammer</span>
            <span className={styles.playerName}>Aswin Sudarsanan</span>
            <span className={styles.playerRole}>Batting all-rounder · RHB</span>
            <span className={styles.base}>Base ₹20,000</span>
          </div>
        </div>
        <div className={styles.bidBlock}>
          <span className={styles.label}>Current bid</span>
          <div className={styles.bidWrap}>
            <span ref={glowRef} className={styles.glow} />
            <span ref={bidRef} className={styles.bid}>
              {formatInr(STEPS[0][1])}
            </span>
          </div>
          <div className={styles.leaderSlot}>
            <div ref={leaderRef} className={styles.leader}>
              <span className={styles.badge} style={{ background: leader.color }}>
                {leader.short}
              </span>
              <span className={styles.leaderName}>{leader.name}</span>
              <span className={styles.leading}>leading</span>
            </div>
          </div>
        </div>
        <div className={styles.purses}>
          <span className={styles.purseLabel}>Purse left</span>
          {PURSES.map(([id, purse]) => {
            const left = purse - (sold && id === "tt" ? amount : 0);
            return (
              <div key={id} className={styles.purseRow}>
                <div className={styles.purseTop}>
                  <span>{FRANCHISES[id].name}</span>
                  <span className={styles.purseAmount}>{formatInr(left)}</span>
                </div>
                <div className={styles.track}>
                  <div
                    className={styles.fill}
                    style={{ background: id === leaderId && !sold ? "var(--auction-gold)" : "var(--outline)", transform: `scaleX(${(left / 500000).toFixed(3)})` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
        {sold && (
          <div className={styles.soldScrim}>
            <div ref={stampRef} className={styles.stamp}>
              <span className={styles.stampWord}>
                <Icon name="gavel" size={40} />
                SOLD
              </span>
              <span className={styles.stampMeta}>₹1,20,000 · Victory CC</span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
