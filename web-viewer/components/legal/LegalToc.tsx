"use client";

import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import styles from "./Legal.module.css";

export type TocItem = { id: string; title: string };

/**
 * Tracks which section heading is in the upper part of the viewport, falling back to the first item. A
 * clicked entry wins: near the end of a short page its heading can't reach the top, so the observer alone
 * would keep an earlier section current.
 */
function useCurrentSection(items: TocItem[]) {
  const [current, setCurrent] = useState(items[0]?.id ?? null);
  const pickedAt = useRef(0);
  useEffect(() => {
    if (typeof IntersectionObserver === "undefined") return;
    const headings = items.map((item) => document.getElementById(item.id)).filter((el): el is HTMLElement => el !== null);
    const visible = new Set<string>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) visible.add(entry.target.id);
          else visible.delete(entry.target.id);
        }
        const first = items.find((item) => visible.has(item.id));
        if (first && Date.now() - pickedAt.current > 1000) setCurrent(first.id);
      },
      // A heading counts once it is in the top 40% of the viewport.
      { rootMargin: "0px 0px -60% 0px" },
    );
    headings.forEach((heading) => observer.observe(heading));
    return () => observer.disconnect();
  }, [items]);
  const pick = (id: string) => {
    pickedAt.current = Date.now();
    setCurrent(id);
  };
  return { current, pick };
}

function TocList({ items, current, onPick }: { items: TocItem[]; current: string | null; onPick: (id: string) => void }) {
  return (
    <ol className={styles.tocList}>
      {items.map((item) => (
        <li key={item.id}>
          <a href={`#${item.id}`} onClick={() => onPick(item.id)} className={item.id === current ? styles.tocCurrent : undefined} aria-current={item.id === current ? "location" : undefined}>
            {item.title}
          </a>
        </li>
      ))}
    </ol>
  );
}

/**
 * Design update #5 B3 table of contents: a sticky sidebar from 1024 px, a collapsed "On this page"
 * disclosure below. Both render; CSS shows the one that fits.
 */
export function LegalToc({ items, variant }: { items: TocItem[]; variant: "sidebar" | "disclosure" }) {
  const { current, pick } = useCurrentSection(items);
  if (variant === "sidebar") {
    return (
      <nav aria-label="On this page" className={styles.tocSidebar}>
        <span className={styles.tocLabel}>On this page</span>
        <TocList items={items} current={current} onPick={pick} />
      </nav>
    );
  }
  return (
    <details className={styles.tocDisclosure}>
      <summary>
        On this page
        <Icon name="expand_more" size={22} className={styles.tocChevron} />
      </summary>
      <nav aria-label="On this page">
        <TocList items={items} current={current} onPick={pick} />
      </nav>
    </details>
  );
}
