"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { del, get, onSessionEnded, post, qs } from "@/lib/api";
import { ago, LEAGUE_STATUS, roleLabel, time } from "@/lib/format";
import { useApi, useSlashKey, useTheme, type ThemeChoice } from "@/lib/hooks";
import type { Health, SearchResult, Session, Today } from "@/lib/types";
import { Avatar, Btn, Dialog, Icon, LeagueCell, PersonCell, Seg, Status } from "./ui";

type ShellState = { session: Session; live: { label: string; stale: boolean } | null; setLive: (l: { label: string; stale: boolean } | null) => void };

const ShellContext = createContext<ShellState | null>(null);

export function useShell(): ShellState {
  const ctx = useContext(ShellContext);
  if (!ctx) throw new Error("useShell outside the admin shell");
  return ctx;
}

const NAV: [string, string, string][] = [
  ["/", "Overview", "monitoring"],
  ["/reports/", "Reports", "flag"],
  ["/leagues/", "Leagues", "emoji_events"],
  ["/users/", "Users", "group"],
  ["/suspended/", "Suspended", "block"],
  ["/audit/", "Audit log", "history"],
];

const PHONE_TABS: [string, string, string][] = [
  ["/", "Overview", "monitoring"],
  ["/reports/", "Reports", "flag"],
  ["/leagues/", "Leagues", "emoji_events"],
  ["/users/", "Users", "group"],
  ["/audit/", "More", "more_horiz"],
];

const isActive = (href: string, path: string) => (href === "/" ? path === "/" : path.startsWith(href));

export function signInHref(): string {
  if (typeof window === "undefined") return "/signin/";
  return `/signin/${qs({ next: window.location.pathname + window.location.search })}`;
}

/** Everything after sign-in sits in here: it checks the session first and never renders data without one. */
export function Shell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [session, setSession] = useState<Session | null>(null);
  const [ended, setEnded] = useState(false);
  const [live, setLive] = useState<{ label: string; stale: boolean } | null>(null);
  useTheme();

  useEffect(() => {
    get<Session>("/api/v1/admin/session")
      .then(setSession)
      .catch(() => router.replace(signInHref()));
    return onSessionEnded(() => setEnded(true));
  }, [router]);

  if (!session) return null;
  return (
    <ShellContext.Provider value={{ session, live, setLive }}>
      <div className="shell">
        <Sidebar />
        <div className="mainc">
          <Topbar />
          <main className="mainb">{children}</main>
        </div>
        <PhoneTabs />
      </div>
      {ended && (
        <Dialog title="Your session ended" width={440} actions={<Btn kind="primary" onClick={() => router.push(signInHref())}>Sign in with Google</Btn>}>
          <p>Sessions last 30 minutes, and end at once when you sign out on another device. Sign in again to carry on from this page; nothing you were looking at is lost.</p>
        </Dialog>
      )}
    </ShellContext.Provider>
  );
}

function Sidebar() {
  const path = usePathname();
  const today = useApi<Today>("/api/v1/admin/insights/today");
  const health = useApi<Health>("/api/v1/admin/health");
  const [menu, setMenu] = useState(false);
  const { reload: reloadToday } = today;
  const { reload: reloadHealth } = health;
  useEffect(() => {
    const t = window.setInterval(() => {
      reloadToday();
      reloadHealth();
    }, 60_000);
    return () => window.clearInterval(t);
  }, [reloadToday, reloadHealth]);
  const open = today.data?.moderation.openReports ?? 0;
  const warnings = health.data ? healthWarnings(health.data).length : 0;
  return (
    <aside className="side">
      <div className="brandrow">
        <span className="logo">C</span>
        <span className="wm">
          Crichere<small>Admin</small>
        </span>
      </div>
      <nav aria-label="Admin">
        {NAV.map(([href, label, icon]) => (
          <Link key={href} href={href} className={`nav${isActive(href, path) ? " on" : ""}`} aria-current={isActive(href, path) ? "page" : undefined}>
            <Icon name={icon} />
            {label}
            {label === "Reports" && open > 0 && <span className="cnt">{open}</span>}
          </Link>
        ))}
      </nav>
      <div className="sidefoot">
        {warnings > 0 && (
          <Link href="/#health" className="sub hw">
            <Icon name="error" className="warn" />
            {warnings} health warning{warnings === 1 ? "" : "s"}
          </Link>
        )}
        <AccountButton open={menu} setOpen={setMenu} />
      </div>
    </aside>
  );
}

/** Checks that need attention, shared by the sidebar and the overview's health card. */
export function healthWarnings(h: Health): string[] {
  const out: string[] = [];
  if (h.nightly.status && h.nightly.status !== "SUCCEEDED") out.push("Nightly summary");
  if (h.nightly.finishedAt && Date.now() - new Date(h.nightly.finishedAt).getTime() > 26 * 3600_000) out.push("Nightly summary late");
  if (h.otp.failureRate != null && h.otp.codesSentToday >= 20 && h.otp.failureRate > 0.25) out.push("Sign-in codes");
  if (h.sms.sentToday >= h.sms.dailyCap * 0.8) out.push("SMS today");
  if ((h.ses.bounces14d ?? 0) > 0 || (h.ses.complaints14d ?? 0) > 0) out.push("Alert emails");
  return out;
}

function AccountButton({ open, setOpen }: { open: boolean; setOpen: (v: boolean) => void }) {
  const { session } = useShell();
  const router = useRouter();
  const [theme, setTheme] = useTheme();
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => !box.current?.contains(e.target as Node) && setOpen(false);
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    window.addEventListener("mousedown", close);
    window.addEventListener("keydown", esc);
    return () => {
      window.removeEventListener("mousedown", close);
      window.removeEventListener("keydown", esc);
    };
  }, [open, setOpen]);

  const signOut = async (everywhere: boolean) => {
    try {
      if (everywhere) await post("/api/v1/admin/session/sign-out-everywhere");
      else await del("/api/v1/admin/session");
    } finally {
      router.replace("/signin/");
    }
  };

  const name = session.email.split("@")[0];
  return (
    <div ref={box}>
      {open && (
        <div className="menu" style={{ left: 0, bottom: 56 }} role="menu">
          <div className="col" style={{ padding: "10px 10px 8px" }}>
            <span className="strong">{session.email}</span>
            <span className="sub">Session ends at {time(session.expiresAt)}</span>
          </div>
          <hr />
          <div style={{ padding: "6px 10px", display: "flex", flexDirection: "column", gap: 8 }}>
            <span className="sub">Theme</span>
            <Seg<ThemeChoice> label="Theme" items={[["device", "Device"], ["light", "Light"], ["dark", "Dark"]]} value={theme} onChange={setTheme} />
          </div>
          <hr />
          <div className="mi" role="menuitem">
            <Icon name="devices" />
            Active sessions
            <span className="num sub" style={{ marginLeft: "auto" }}>
              {session.liveSessions ?? 1}
            </span>
          </div>
          <button type="button" className="mi" role="menuitem" onClick={() => signOut(false)}>
            <Icon name="logout" />
            Sign out
          </button>
          <button type="button" className="mi" role="menuitem" style={{ color: "var(--dangerc)" }} onClick={() => signOut(true)}>
            <Icon name="phonelink_erase" className="danger" />
            Sign out everywhere
          </button>
        </div>
      )}
      <button type="button" className="who" onClick={() => setOpen(!open)} aria-haspopup="menu" aria-expanded={open}>
        <Avatar name={name} />
        <div className="col">
          <span className="strong">{name}</span>
          <span className="sub">Admin</span>
        </div>
        <Icon name="unfold_more" className="muted" />
      </button>
    </div>
  );
}

function Topbar() {
  const { live } = useShell();
  const [searching, setSearching] = useState(false);
  const open = useCallback(() => setSearching(true), []);
  useSlashKey(open);
  return (
    <div className="topbar">
      <button type="button" className="search" onClick={open} aria-label="Search leagues and people">
        <Icon name="search" />
        Search leagues and people
        <kbd>/</kbd>
      </button>
      <span style={{ marginLeft: "auto" }}>
        {live && (
          <span className={`live${live.stale ? " stale" : ""}`} aria-live="polite">
            <i />
            {live.label}
          </span>
        )}
      </span>
      {searching && <SearchDialog onClose={() => setSearching(false)} />}
    </div>
  );
}

/** Board S5: names, or a pasted id. Never phone numbers (phone lookup is on Users). */
function SearchDialog({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [result, setResult] = useState<SearchResult | null>(null);
  const [cursor, setCursor] = useState(0);

  useEffect(() => {
    if (q.trim().length < 2) return;
    const t = window.setTimeout(() => {
      get<SearchResult>(`/api/v1/admin/search${qs({ q: q.trim() })}`)
        .then((r) => {
          setResult(r);
          setCursor(0);
        })
        .catch(() => setResult({ leagues: [], users: [] }));
    }, 200);
    return () => window.clearTimeout(t);
  }, [q]);

  const shown = q.trim().length < 2 ? null : result;
  const items = [
    ...(shown?.leagues ?? []).map((l) => ({ href: `/leagues/view/${qs({ id: l.id })}`, node: <LeagueCell name={l.name} logo={l.logoUrl} sub={`${l.district}, ${LEAGUE_STATUS[l.status]?.[1].toLowerCase()}`} />, live: l.status === "auction_live", group: "Leagues" })),
    ...(shown?.users ?? []).map((u) => ({ href: `/users/view/${qs({ id: u.id })}`, node: <PersonCell name={u.name} photo={u.photoUrl} sub={[roleLabel(u.playingRole), u.district].filter((s) => s && s !== "—").join(", ")} />, live: false, group: "People" })),
  ];
  const go = (href: string) => {
    onClose();
    router.push(href);
  };

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <div className="menu searchbox" role="dialog" aria-label="Search">
        <div className="input">
          <Icon name="search" />
          <input
            autoFocus
            value={q}
            placeholder="League or person name, or an ID"
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Escape") onClose();
              if (e.key === "ArrowDown") setCursor((c) => Math.min(items.length - 1, c + 1));
              if (e.key === "ArrowUp") setCursor((c) => Math.max(0, c - 1));
              if (e.key === "Enter" && items[cursor]) go(items[cursor].href);
            }}
          />
          <kbd>Esc</kbd>
        </div>
        {shown && items.length === 0 && <span className="sub" style={{ padding: "10px" }}>Nothing matches.</span>}
        {items.map((it, i) => (
          <SearchItem key={it.href} first={i === 0 || items[i - 1].group !== it.group} group={it.group} on={i === cursor} onClick={() => go(it.href)}>
            {it.node}
            {it.live && <span style={{ marginLeft: "auto" }}><Status tone="live">Live</Status></span>}
          </SearchItem>
        ))}
        <hr />
        <span className="sub" style={{ padding: "4px 10px 6px" }}>
          Paste a league or user ID to go straight to it. <kbd>↑</kbd> <kbd>↓</kbd> to move, <kbd>Enter</kbd> to open
        </span>
      </div>
    </>
  );
}

const SearchItem = ({ first, group, on, onClick, children }: { first: boolean; group: string; on: boolean; onClick: () => void; children: ReactNode }) => (
  <>
    {first && <span className="sub" style={{ padding: "10px 10px 4px" }}>{group}</span>}
    <button type="button" className={`mi${on ? " on" : ""}`} onClick={onClick}>
      {children}
    </button>
  </>
);

function PhoneTabs() {
  const path = usePathname();
  return (
    <nav className="ptabs" aria-label="Admin sections">
      {PHONE_TABS.map(([href, label, icon]) => (
        <Link key={label} href={href} className={isActive(href, path) ? "on" : undefined}>
          <Icon name={icon} />
          {label}
        </Link>
      ))}
    </nav>
  );
}

export function PageHead({ title, crumb, sub, actions }: { title: ReactNode; crumb?: ReactNode; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="pagehead">
      <div className="col" style={{ gap: 6 }}>
        {crumb && <span className="crumb">{crumb}</span>}
        <h1>{title}</h1>
        {sub && <span className="sub">{sub}</span>}
      </div>
      {actions && <div className="hacts">{actions}</div>}
    </div>
  );
}

export const liveLabel = (lastEventAt: number | null, stale: boolean) =>
  lastEventAt == null ? "Connecting…" : stale ? `Paused, last update ${ago(new Date(lastEventAt).toISOString())}` : `Live, updated ${ago(new Date(lastEventAt).toISOString())}`;
