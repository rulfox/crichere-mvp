"use client";

import { useEffect, useId, useRef, useState } from "react";
import { Icon } from "./Icon";
import { StoreBadge } from "./StoreBadge";
import styles from "./GetAppPopover.module.css";

/** Live Auction top bar's "Get the app" pill and its 250px light popover with both store badges. */
export function GetAppPopover() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelId = useId();

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div ref={rootRef} className={styles.root}>
      <button
        ref={buttonRef}
        type="button"
        className={styles.button}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
        aria-label="Get the app"
      >
        <Icon name="download" size={17} />
        {/* Design update #4 W1: "Get app" below 480px. */}
        <span className={styles.labelWide}>Get the app</span>
        <span className={styles.labelNarrow}>Get app</span>
      </button>
      {open && (
        <div id={panelId} role="dialog" aria-label="Get the Crichere app" className={styles.panel}>
          <span className={styles.title}>Watch on the Crichere app</span>
          <span className={styles.copy}>Faster updates, sold alerts, and your own league in your pocket.</span>
          <StoreBadge kind="play" />
          <StoreBadge kind="appstore" />
        </div>
      )}
    </div>
  );
}
