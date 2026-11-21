"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { API_BASE, ApiError, get } from "./api";
import type { LiveSnapshot } from "./types";

export type Loaded<T> = {
  data: T | null;
  error: ApiError | null;
  /** True while a request is in flight; `data` keeps the previous answer meanwhile (board X4). */
  loading: boolean;
  reload: () => void;
};

/** GETs `path` (null = wait), keeps the last answer while refetching, and never blanks on error. */
export function useApi<T>(path: string | null): Loaded<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [tick, setTick] = useState(0);
  // The request that last finished; while it differs from the current one, a load is in flight.
  const [settled, setSettled] = useState<string | null>(null);
  const key = path == null ? null : `${path}#${tick}`;

  useEffect(() => {
    if (path == null || key == null) return;
    const controller = new AbortController();
    get<T>(path, controller.signal)
      .then((d) => {
        setData(d);
        setError(null);
        setSettled(key);
      })
      .catch((e) => {
        if ((e as Error)?.name === "AbortError") return;
        setError(e instanceof ApiError ? e : new ApiError(0, "UNKNOWN", String(e)));
        setSettled(key);
      });
    return () => controller.abort();
  }, [path, key]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading: key != null && settled !== key, reload };
}

export type LiveState = { snapshot: LiveSnapshot | null; lastEventAt: number | null; stale: boolean };

const STALE_AFTER_MS = 15_000;

/**
 * The admin live stream (GET /api/v1/admin/live). The backend sends a snapshot when something
 * changed and a `ping` every 10 s otherwise; 15 s without either marks the view stale (board X3),
 * keeping the last snapshot on screen. Closes when the tab is hidden, so nothing runs unwatched.
 */
export function useLive(enabled = true): LiveState {
  const [snapshot, setSnapshot] = useState<LiveSnapshot | null>(null);
  const [lastEventAt, setLastEventAt] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const source = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled || typeof window === "undefined" || typeof EventSource === "undefined") return;
    const open = () => {
      if (source.current || document.visibilityState !== "visible") return;
      const es = new EventSource(`${API_BASE}/api/v1/admin/live`, { withCredentials: true });
      es.addEventListener("snapshot", (e) => {
        setSnapshot(JSON.parse((e as MessageEvent).data) as LiveSnapshot);
        setLastEventAt(Date.now());
      });
      es.addEventListener("ping", () => setLastEventAt(Date.now()));
      source.current = es;
    };
    const close = () => {
      source.current?.close();
      source.current = null;
    };
    const onVisibility = () => (document.visibilityState === "visible" ? open() : close());
    open();
    document.addEventListener("visibilitychange", onVisibility);
    const clock = window.setInterval(() => setNow(Date.now()), 1000);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      window.clearInterval(clock);
      close();
    };
  }, [enabled]);

  const stale = lastEventAt == null ? snapshot != null : now - lastEventAt > STALE_AFTER_MS;
  return { snapshot, lastEventAt, stale };
}

export type ThemeChoice = "device" | "light" | "dark";
const THEME_KEY = "crichere-admin-theme";
const THEME_EVENT = "crichere-admin-theme";

/** Device by default, remembered in this browser (board S4). */
export function useTheme(): [ThemeChoice, (t: ThemeChoice) => void] {
  const [choice, setChoice] = useState<ThemeChoice>("device");
  useEffect(() => {
    const read = () => {
      const saved = window.localStorage.getItem(THEME_KEY) as ThemeChoice | null;
      setChoice(saved === "light" || saved === "dark" ? saved : "device");
    };
    read();
    // Every hook instance (shell, account menu, sign-in page) follows the same choice.
    window.addEventListener(THEME_EVENT, read);
    return () => window.removeEventListener(THEME_EVENT, read);
  }, []);
  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const apply = () => {
      const dark = choice === "dark" || (choice === "device" && media.matches);
      document.documentElement.dataset.theme = dark ? "dark" : "light";
    };
    apply();
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }, [choice]);
  const set = useCallback((t: ThemeChoice) => {
    if (t === "device") window.localStorage.removeItem(THEME_KEY);
    else window.localStorage.setItem(THEME_KEY, t);
    window.dispatchEvent(new Event(THEME_EVENT));
  }, []);
  return [choice, set];
}

/** Press "/" anywhere outside a field to open search (board S5). */
export function useSlashKey(onSlash: () => void) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      const typing = target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable);
      if (e.key === "/" && !typing && !e.metaKey && !e.ctrlKey) {
        e.preventDefault();
        onSlash();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [onSlash]);
}

/**
 * Previous/Next over keyset cursors (boards L1, U1, M1): the backend only knows "after this row",
 * so earlier pages are kept as a stack. Changing the filters starts again from the first page.
 */
export function useCursorPages<Total = unknown>(filterKey: string) {
  const [state, setState] = useState<{ key: string; stack: string[]; total: Total | null }>({ key: filterKey, stack: [], total: null });
  const stack = state.key === filterKey ? state.stack : [];
  const cursor = stack.length ? stack[stack.length - 1] : null;
  return {
    cursor,
    page: stack.length,
    /** The total from the first page, kept while paging (later pages don't count again). */
    total: state.key === filterKey ? state.total : null,
    next: (c: string, total?: Total | null) => setState({ key: filterKey, stack: [...stack, c], total: total ?? (state.key === filterKey ? state.total : null) }),
    prev: stack.length ? () => setState({ key: filterKey, stack: stack.slice(0, -1), total: state.key === filterKey ? state.total : null }) : undefined,
  };
}
