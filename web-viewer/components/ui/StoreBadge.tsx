"use client";

import type { MouseEvent } from "react";
import { prefersReducedMotion } from "@/lib/motion";
import { APP_STORE_URL, PLAY_STORE_URL } from "@/lib/store";
import { Icon } from "./Icon";
import styles from "./StoreBadge.module.css";

type StoreBadgeProps =
  | {
      kind: "play" | "appstore";
      /** Store listing URL. Defaults to the env-configured one; `null` (not published yet) renders the coming-soon variant. */
      url?: string | null;
    }
  | {
      kind: "qr";
      /** A pre-generated QR image for the Play Store URL; the card renders nothing until one exists (docs/PHASE11.md D8). */
      qrSrc?: string | null;
    };

/**
 * Store Badge.dc.html. The glyphs are the design's mock artwork -- they must be swapped for the
 * official Google Play / Apple badges before release (DESIGN-REVIEW follow-up).
 */
export function StoreBadge(props: StoreBadgeProps) {
  if (props.kind === "qr") {
    if (!props.qrSrc) return null;
    return (
      <div className={styles.qr}>
        {/* eslint-disable-next-line @next/next/no-img-element -- a generated SVG, nothing to optimise */}
        <img src={props.qrSrc} alt="QR code to download Crichere" className={styles.qrImage} />
        <span className={styles.qrCaption}>Scan · Android app</span>
      </div>
    );
  }

  const isPlay = props.kind === "play";
  const url = props.url === undefined ? (isPlay ? PLAY_STORE_URL : APP_STORE_URL) : props.url;
  const storeName = isPlay ? "Google Play" : "App Store";

  if (!url) {
    return (
      <span role="img" aria-label={`${storeName}, coming soon`} className={`${styles.badge} ${isPlay ? styles.play : styles.apple} ${styles.soon}`}>
        {isPlay ? <PlayMark /> : <Icon name="phone_iphone" size={28} />}
        <span className={styles.text}>
          <span className={styles.over}>Coming soon on</span>
          <span className={styles.store}>{storeName}</span>
        </span>
        <span className={styles.pill}>{isPlay ? "SOON" : "iOS SOON"}</span>
      </span>
    );
  }

  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={isPlay ? "Get it on Google Play" : "Download on the App Store"}
      className={`${styles.badge} ${isPlay ? styles.play : styles.apple}`}
      onMouseMove={magnetMove}
      onMouseLeave={magnetLeave}
    >
      {isPlay ? <PlayMark /> : <Icon name="phone_iphone" size={28} />}
      <span className={styles.text}>
        <span className={`${styles.over} ${isPlay ? styles.overPlay : ""}`}>{isPlay ? "Get it on" : "Download on the"}</span>
        <span className={styles.store}>{storeName}</span>
      </span>
    </a>
  );
}

/** Magnetic hover: follows the pointer up to 10px across / 8px down, lifted 2px. Reduced motion keeps the shadow only. */
function magnetMove(event: MouseEvent<HTMLElement>) {
  if (prefersReducedMotion()) return;
  const el = event.currentTarget;
  const rect = el.getBoundingClientRect();
  const dx = (event.clientX - rect.left) / rect.width - 0.5;
  const dy = (event.clientY - rect.top) / rect.height - 0.5;
  el.style.transition = "transform 120ms ease-out, box-shadow 300ms ease";
  el.style.transform = `translate(${dx * 10}px, ${dy * 8 - 2}px)`;
}

function magnetLeave(event: MouseEvent<HTMLElement>) {
  const el = event.currentTarget;
  el.style.transition = "";
  el.style.transform = "";
}

/** The design's simplified Play mark (placeholder for the official badge). */
function PlayMark() {
  return (
    <svg width="22" height="24" viewBox="0 0 24 26" aria-hidden="true" style={{ flex: "none" }}>
      <polygon points="1,1 13,13 1,25" fill="#00A0FF" />
      <polygon points="1,1 17,10 13,13" fill="#00D26A" />
      <polygon points="1,25 13,13 17,16" fill="#FF3A44" />
      <polygon points="17,10 23,13 17,16 13,13" fill="#FFC400" />
    </svg>
  );
}
