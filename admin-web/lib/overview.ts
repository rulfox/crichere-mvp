import { addDays, change, day, num } from "./format";
import { M, Metrics, mondayOf, topAndRest } from "./metrics";
import type { Health, History, Today } from "./types";

/*
 * Everything the overview shows, derived in one place so the one-line summaries can never disagree
 * with the charts under them (board O1 note). Parts with `b` render bold; only figures are bold.
 */

export type Part = { t: string; b?: boolean };
export type Sentence = Part[];

export type RangeKey = "today" | "7" | "30" | "90";

export type Kpi = { label: string; value: string; delta: string; up: boolean; sub: string; spark: number[] };

export type Overview = {
  range: { from: string; to: string; days: number; label: string };
  kpis: Kpi[];
  open: { count: number; oldestAt: string | null };
  sentences: { live: Sentence; growth: Sentence; engagement: Sentence; leagues: Sentence; trust: Sentence; health: Sentence };
  newUsers: { days: string[]; values: number[]; previous: number[] | null };
  funnel: { key: string; value: number }[];
  active: { days: string[]; dau: number[]; wau: number[]; mau: number[] };
  retention: { label: string; pct: (number | null)[] }[];
  status: { key: string; value: number }[];
  auctionsWeekly: { week: string; value: number }[];
  districts: { key: string; value: number }[];
  reports14: { days: string[]; opened: number[]; closed: number[] };
  medianCloseHours: number | null;
  openByReason: { key: string; value: number }[];
  healthRows: { ok: boolean; name: string; text: string }[];
};

const RANGE_DAYS: Record<RangeKey, number> = { today: 7, "7": 7, "30": 30, "90": 90 };

/** History to fetch: the shown range plus the period before it, and at least 13 weeks for cohorts. */
export function historyWindow(range: RangeKey, yesterday: string): { from: string; to: string } {
  const n = RANGE_DAYS[range];
  return { from: addDays(yesterday, -Math.max(2 * n, 91) + 1), to: yesterday };
}

export const STATUS_ORDER: [string, string][] = [
  ["open", "Registration open"],
  ["auction_live", "Auction live"],
  ["auction_done", "Auction done"],
  ["completed", "Completed"],
];

const plural = (n: number, one: string, many = `${one}s`) => `${num(n)} ${n === 1 ? one : many}`;

export function buildOverview(history: History, today: Today, health: Health | null, range: RangeKey, yesterday: string, compare: boolean): Overview {
  const m = new Metrics(history.rows);
  const n = RANGE_DAYS[range];
  const to = yesterday;
  const from = addDays(to, -n + 1);
  const prevTo = addDays(from, -1);
  const prevFrom = addDays(prevTo, -n + 1);
  const live = today.live;
  const label = range === "today" ? "today" : `the last ${n} days`;

  // ---- KPIs
  const newInRange = range === "today" ? live.today.signUps : m.sum(M.newUsers, from, to);
  const newBefore = range === "today" ? m.value(M.newUsers, to) : m.sum(M.newUsers, prevFrom, prevTo);
  const usersTotal = (m.latest(M.usersTotal, to) ?? 0) + live.today.signUps;
  const mau = m.latest(M.mau, to) ?? 0;
  const activeLeagues = m.latest(M.activeLeagues, to) ?? 0;
  const leaguesNew = range === "today" ? live.today.leagues : m.sum(M.leaguesCreated, from, to);
  const auctions = m.sum(M.auctionsCompleted, from, to);
  const auctionsBefore = m.sum(M.auctionsCompleted, prevFrom, prevTo);
  const series = (metric: string) => m.series(metric, from, to).map((p) => p.value);

  const kpis: Kpi[] = [
    { label: "Users", value: num(usersTotal), delta: `+${num(newInRange)} new`, up: true, sub: `${change(newInRange, newBefore)} vs previous ${range === "today" ? "day" : `${n} days`}`, spark: series(M.newUsers) },
    {
      label: "Active today",
      value: num(live.today.activeUsers),
      delta: mau ? (mau < 20 ? `${num(live.today.activeUsers)} of ${num(mau)} monthly` : `${Math.round((live.today.activeUsers / mau) * 100)}% of monthly`) : "No monthly figure yet",
      up: true,
      sub: `Monthly active ${num(mau)}`,
      spark: series(M.dau),
    },
    { label: "Active leagues", value: num(activeLeagues), delta: `+${num(leaguesNew)}`, up: true, sub: `${plural(live.now.liveAuctions, "auction")} live now`, spark: series(M.activeLeagues) },
    {
      label: "Auctions completed",
      value: num(auctions),
      delta: change(auctions, auctionsBefore),
      up: auctions >= auctionsBefore,
      sub: range === "today" ? "Last 7 days" : `Last ${n} days`,
      spark: m.weekly(M.auctionsCompleted, 8, to).map((w) => w.value),
    },
  ];

  // ---- charts
  const days = m.series(M.newUsers, from, to).map((p) => p.day);
  const newUsers = { days, values: series(M.newUsers), previous: compare ? m.series(M.newUsers, prevFrom, prevTo).map((p) => p.value) : null };
  const funnel = [
    { key: "Code sent", value: m.sum(M.otpSent, from, to) },
    { key: "Signed in", value: m.sum(M.otpSignedIn, from, to) },
    { key: "Profile done", value: m.sum(M.cohortProfileDone, from, to) },
    { key: "Joined a league", value: m.sum(M.cohortJoined, from, to) },
  ];
  const active = { days, dau: series(M.dau), wau: series(M.wau), mau: series(M.mau) };
  const retention = m.retention(6, to).map((c) => ({ label: day(c.week), pct: c.pct }));
  const split = new Map(m.split(M.leaguesByStatus, to).map((r) => [r.key, r.value]));
  const status = STATUS_ORDER.map(([k, l]) => ({ key: l, value: split.get(k) ?? 0 }));
  const auctionsWeekly = m.weekly(M.auctionsCompleted, 8, to);
  const districts = topAndRest(
    m.split(M.usersByDistrict, to).map((r) => ({ key: r.key.split("|")[1] ?? r.key, value: r.value })),
    5,
    "Other districts",
  );
  const d14 = m.series(M.reportsOpened, addDays(to, -13), to);
  const reports14 = { days: d14.map((p) => p.day), opened: d14.map((p) => p.value), closed: m.series(M.reportsClosed, addDays(to, -13), to).map((p) => p.value) };
  const medianMinutes = m.latest(M.reportCloseMedian, to);
  const medianCloseHours = medianMinutes == null || m.sum(M.reportsClosed, addDays(to, -29), to) === 0 ? null : Math.round((medianMinutes / 60) * 10) / 10;
  const openByReason = Object.entries(today.moderation.openByReason)
    .map(([key, value]) => ({ key, value }))
    .sort((a, b) => b.value - a.value);

  // ---- sentences
  const liveSentence: Sentence =
    live.now.liveAuctions === 0
      ? [{ t: "No auctions are running right now." }]
      : [
          { t: plural(live.now.liveAuctions, "auction"), b: true },
          { t: live.now.liveAuctions === 1 ? " is running with " : " are running with " },
          { t: plural(live.now.auctionViewers, "person", "people"), b: true },
          { t: ` watching. Bids are coming in at ` },
          { t: num(live.now.bidsLastMinute), b: true },
          { t: " a minute." },
        ];

  const growth: Sentence = [{ t: plural(newInRange, "person", "people"), b: true }, { t: ` joined ${label}` }];
  if (range !== "today") {
    const diff = newInRange - newBefore;
    growth.push({ t: diff === 0 ? ", the same as the period before." : ", " }, ...(diff === 0 ? [] : [{ t: `${num(Math.abs(diff))} ${diff > 0 ? "more" : "fewer"}`, b: true }, { t: ` than the ${n} days before.` }]));
  } else growth.push({ t: "." });
  const drop = biggestDrop(funnel);
  if (drop) growth.push({ t: ` The biggest drop is after ${drop.after.toLowerCase()}: ` }, { t: `${drop.pct}%`, b: true }, { t: ` of people at that step go on to ${drop.next.toLowerCase()}.` });

  const ratio = mau > 0 ? Math.max(1, Math.round(mau / Math.max(1, live.today.activeUsers))) : null;
  const engagement: Sentence = [{ t: plural(live.today.activeUsers, "person", "people"), b: true }, { t: " used Crichere today." }];
  if (ratio && live.today.activeUsers > 0) engagement.push({ t: " About " }, { t: `1 in ${ratio}`, b: true }, { t: " monthly users comes back on a given day." });

  const totalDistrictUsers = districts.reduce((s, d) => s + d.value, 0);
  const leaguesSentence: Sentence = [{ t: plural(activeLeagues, "league"), b: true }, { t: activeLeagues === 1 ? " is active." : " are active." }];
  const topDistrict = districts.find((d) => d.key !== "Other districts");
  if (topDistrict && totalDistrictUsers > 0) {
    leaguesSentence.push({ t: " " }, { t: topDistrict.key, b: true }, { t: " has the most users" });
    leaguesSentence.push(totalDistrictUsers < 20 ? { t: ` (${num(topDistrict.value)} of ${num(totalDistrictUsers)}).` } : { t: ` (${Math.round((topDistrict.value / totalDistrictUsers) * 100)}%).` });
  }

  const open = today.moderation.openReports;
  const trust: Sentence = [];
  if (medianCloseHours != null) {
    trust.push({ t: "Reports close in a median of " }, { t: hours(medianCloseHours), b: true }, { t: medianCloseHours <= 24 ? ", inside the 24-hour target. " : ", outside the 24-hour target. " });
  } else trust.push({ t: "No reports were closed in the last 30 days. " });
  trust.push({ t: num(open), b: true }, { t: open === 1 ? " is open now." : " are open now." });

  const healthRows = health ? healthChecks(health) : [];
  const bad = healthRows.filter((h) => !h.ok);
  const healthSentence: Sentence = health == null ? [{ t: "Checks couldn't be loaded." }] : bad.length === 0 ? [{ t: "Everything is working." }] : [{ t: plural(bad.length, "check"), b: true }, { t: ` need${bad.length === 1 ? "s" : ""} attention: ${bad.map((b) => b.name).join(", ")}.` }];

  return {
    range: { from, to, days: n, label },
    kpis,
    open: { count: open, oldestAt: today.moderation.oldestOpenAt },
    sentences: { live: liveSentence, growth, engagement, leagues: leaguesSentence, trust, health: healthSentence },
    newUsers,
    funnel,
    active,
    retention,
    status,
    auctionsWeekly,
    districts,
    reports14,
    medianCloseHours,
    openByReason,
    healthRows,
  };
}

function biggestDrop(rows: { key: string; value: number }[]): { after: string; next: string; pct: number } | null {
  let worst: { after: string; next: string; pct: number } | null = null;
  for (let i = 1; i < rows.length; i++) {
    if (rows[i - 1].value < 20) continue; // small numbers: no percentages
    const pct = Math.round((rows[i].value / rows[i - 1].value) * 100);
    if (!worst || pct < worst.pct) worst = { after: rows[i - 1].key, next: rows[i].key, pct };
  }
  return worst;
}

export const hours = (h: number) => (h < 1 ? `${Math.max(1, Math.round(h * 60))} minutes` : `${Math.round(h)} hour${Math.round(h) === 1 ? "" : "s"}`);

export function healthChecks(h: Health): { ok: boolean; name: string; text: string }[] {
  const rows: { ok: boolean; name: string; text: string }[] = [];
  if (!h.nightly.status) rows.push({ ok: false, name: "Nightly summary", text: "Hasn't run yet" });
  else {
    const finished = h.nightly.finishedAt ? new Date(h.nightly.finishedAt) : null;
    const took = finished && h.nightly.startedAt ? Math.round((finished.getTime() - new Date(h.nightly.startedAt).getTime()) / 1000) : null;
    const late = finished ? Date.now() - finished.getTime() > 26 * 3600_000 : true;
    rows.push({
      ok: h.nightly.status === "SUCCEEDED" && !late,
      name: "Nightly summary",
      text: h.nightly.status === "SUCCEEDED" ? `Ran ${finished ? finished.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", timeZone: "Asia/Kolkata" }) : ""}${took != null ? `, took ${took} s` : ""}${late ? ", over a day ago" : ""}` : `${h.nightly.status.toLowerCase()}: ${h.nightly.detail ?? ""}`,
    });
  }
  const rate = h.otp.failureRate;
  rows.push({
    ok: !(rate != null && h.otp.codesSentToday >= 20 && rate > 0.25),
    name: "Sign-in codes",
    text: rate == null ? `${num(h.otp.codesSentToday)} sent today` : h.otp.codesSentToday < 20 ? `${num(h.otp.expiredUnusedToday)} of ${num(h.otp.codesSentToday)} expired unused today` : `${(rate * 100).toFixed(1)}% expired unused today`,
  });
  rows.push({ ok: h.sms.sentToday < h.sms.dailyCap * 0.8, name: "SMS today", text: `${num(h.sms.sentToday)} of ${num(h.sms.dailyCap)}` });
  rows.push({ ok: true, name: "MSG91 balance", text: h.msg91Balance == null ? "Not available yet (see owner task O9)" : `${num(h.msg91Balance)} credits` });
  if (!h.ses.configured) rows.push({ ok: true, name: "Alert emails", text: "Not set up on this backend" });
  else {
    const issues = (h.ses.bounces14d ?? 0) + (h.ses.complaints14d ?? 0);
    rows.push({ ok: issues === 0, name: "Alert emails", text: issues === 0 ? "No bounces or complaints in 14 days" : `${num(h.ses.bounces14d)} bounces, ${num(h.ses.complaints14d)} complaints in 14 days` });
  }
  return rows;
}

export { mondayOf };
