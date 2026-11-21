// Display rules for the admin site: India time everywhere, Indian digit grouping, plain labels.

export const TZ = "Asia/Kolkata";

export const num = (n: number | null | undefined): string => (n == null ? "—" : Number(n).toLocaleString("en-IN"));

const dateFmt = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", timeZone: TZ });
const dateYearFmt = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: TZ });
const timeFmt = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: TZ });
const longDayFmt = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", timeZone: TZ });

export const shortDate = (iso: string | null | undefined) => (iso ? dateFmt.format(new Date(iso)) : "—");
export const fullDate = (iso: string | null | undefined) => (iso ? dateYearFmt.format(new Date(iso)) : "—");
export const time = (iso: string | null | undefined) => (iso ? timeFmt.format(new Date(iso)) : "—");
export const dateTime = (iso: string | null | undefined) => (iso ? `${dateYearFmt.format(new Date(iso))}, ${timeFmt.format(new Date(iso))}` : "—");
export const stamp = (iso: string | null | undefined) => (iso ? `${dateFmt.format(new Date(iso))} ${timeFmt.format(new Date(iso))}` : "—");
export const longToday = (now = new Date()) => `${longDayFmt.format(now)}, India time`;

/** A calendar day ("2026-11-21") shown without time-zone drift. */
export const day = (d: string) => dateFmt.format(new Date(`${d}T12:00:00+05:30`));

/** Today in India as YYYY-MM-DD. */
export function todayIst(now = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

export function addDays(d: string, n: number): string {
  const t = new Date(`${d}T00:00:00Z`);
  t.setUTCDate(t.getUTCDate() + n);
  return t.toISOString().slice(0, 10);
}

/** "42 min", "19 h", "3 d": how long something has waited. */
export function age(iso: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `${hours} h`;
  return `${Math.floor(hours / 24)} d`;
}

export function ageHours(iso: string, now = Date.now()): number {
  return (now - new Date(iso).getTime()) / 3600000;
}

export function ago(iso: string, now = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return `${seconds} s ago`;
  return `${age(iso, now)} ago`;
}

export const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

export const REASONS: Record<string, string> = {
  INAPPROPRIATE_IMAGE: "Inappropriate image",
  HATE_OR_HARASSMENT: "Hate or harassment",
  IMPERSONATION: "Impersonation",
  UNDER_18: "Under 18",
  SPAM_OR_MISLEADING: "Spam or misleading",
  BETTING_OR_GAMBLING: "Betting or gambling",
  FAKE_OR_SCAM_LEAGUE: "Fake or scam league",
  SOMETHING_ELSE: "Something else",
};

/** Reasons the board puts first in the queue copy, and colours red. */
export const URGENT_REASONS = new Set(["UNDER_18", "HATE_OR_HARASSMENT", "INAPPROPRIATE_IMAGE"]);

export const reasonLabel = (r: string) => REASONS[r] ?? r;

export const reasonTone = (r: string): Tone => (URGENT_REASONS.has(r) ? "danger" : r === "SOMETHING_ELSE" ? "neutral" : "warn");

export const ACTIONS: Record<string, string> = {
  DISMISS_REPORT: "Dismissed",
  RESOLVE_REPORT: "Resolved",
  REMOVE_IMAGE: "Removed image",
  SUSPEND: "Suspended",
  UNSUSPEND: "Unsuspended",
  RELEASE_NUMBER: "Released number",
  ADMIN_SIGN_IN: "Signed in",
  ADMIN_SIGN_OUT: "Signed out",
  ADMIN_SIGN_OUT_ALL: "Signed out everywhere",
  VIEW_PAYMENT_PROOF: "Viewed payment proof",
  PHONE_LOOKUP: "Phone lookup",
  MARK_INTERNAL: "Marked internal",
  UNMARK_INTERNAL: "Unmarked internal",
  METRICS_BACKFILL: "Rebuilt history",
  ADD_RELEASE_MARKER: "Added release marker",
  DELETE_RELEASE_MARKER: "Deleted release marker",
};

export const actionLabel = (a: string) => ACTIONS[a] ?? a.replace(/_/g, " ").toLowerCase().replace(/^./, (c) => c.toUpperCase());

export type Tone = "ok" | "warn" | "danger" | "info" | "neutral" | "live" | "internal";

export function actionTone(a: string): Tone {
  if (a === "SUSPEND" || a === "REMOVE_IMAGE") return "danger";
  if (a === "DISMISS_REPORT" || a === "RESOLVE_REPORT" || a === "ADMIN_SIGN_IN" || a === "UNSUSPEND") return "ok";
  if (a.includes("INTERNAL") || a.includes("MARKER") || a === "METRICS_BACKFILL") return "info";
  return "neutral";
}

export const LEAGUE_STATUS: Record<string, [Tone, string]> = {
  open: ["info", "Registration open"],
  auction_live: ["live", "Auction live"],
  auction_done: ["ok", "Auction done"],
  completed: ["neutral", "Completed"],
};

export const USER_STATUS: Record<string, [Tone, string]> = {
  active: ["ok", "Active"],
  profile_incomplete: ["neutral", "Profile incomplete"],
  suspended: ["danger", "Suspended"],
  deletion_pending: ["warn", "Deletion requested"],
  deleted: ["neutral", "Deleted"],
};

export const ROLES: Record<string, string> = {
  BATSMAN: "Batter",
  BOWLER: "Bowler",
  ALL_ROUNDER: "All-rounder",
  WICKETKEEPER: "Wicket-keeper",
};

export const roleLabel = (r: string | null | undefined) => (r ? (ROLES[r] ?? r) : "—");

export const FEED: Record<string, string> = {
  SIGN_UP: "New sign-up",
  LEAGUE_CREATED: "New league created",
  PLAYER_JOINED: "Player joined a league",
  REPORT_FILED: "Report filed",
  AUCTION_STARTED: "Auction started",
  AUCTION_FINISHED: "Auction finished",
};

/** Under 20, say "+3", never "+300%" (board X6). */
export function change(current: number, previous: number): string {
  const diff = current - previous;
  const sign = diff > 0 ? "+" : diff < 0 ? "−" : "±";
  if (previous < 20 || current < 20) return `${sign}${num(Math.abs(diff))}`;
  return `${sign}${Math.round((Math.abs(diff) / previous) * 100)}%`;
}

export const pts = (n: number | null | undefined) => (n == null ? "—" : `${num(n)} pts`);
export const rupees = (n: number | null | undefined) => (n == null ? "—" : `₹${num(n)}`);
