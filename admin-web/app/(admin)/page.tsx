"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { ChartPanel, Columns, Funnel, Heat, Legend, LineChart, Meter, Paired, Ranked, SegBar, Spark, SERIES } from "@/components/charts";
import { liveLabel, PageHead, useShell } from "@/components/Shell";
import { Chip, Failed, Icon, Panel, Seg, Words } from "@/components/ui";
import { qs } from "@/lib/api";
import { addDays, age, ageHours, day, FEED, longToday, num, reasonLabel, todayIst } from "@/lib/format";
import { useApi, useLive } from "@/lib/hooks";
import { buildOverview, historyWindow, type RangeKey, type Sentence } from "@/lib/overview";
import type { Health, History, Today } from "@/lib/types";

const RANGES: [RangeKey, string][] = [
  ["today", "Today"],
  ["7", "7 days"],
  ["30", "30 days"],
  ["90", "90 days"],
];

const SectionHead = ({ title, parts }: { title: string; parts: Sentence }) => (
  <div className="sechd">
    <div className="row">
      <h2>{title}</h2>
    </div>
    <Words parts={parts} />
  </div>
);

export default function OverviewPage() {
  const [range, setRange] = useState<RangeKey>("30");
  const [compare, setCompare] = useState(false);
  const yesterday = addDays(todayIst(), -1);
  const window_ = historyWindow(range === "today" ? "7" : range, yesterday);
  const history = useApi<History>(`/api/v1/admin/insights/history${qs(window_)}`);
  const today = useApi<Today>("/api/v1/admin/insights/today");
  const health = useApi<Health>("/api/v1/admin/health");
  const live = useLive();
  const { setLive } = useShell();

  useEffect(() => {
    setLive({ label: liveLabel(live.lastEventAt, live.stale), stale: live.stale || live.lastEventAt == null });
    return () => setLive(null);
  }, [live.lastEventAt, live.stale, setLive]);

  const { reload: reloadToday } = today;
  const { reload: reloadHealth } = health;
  useEffect(() => {
    const t = window.setInterval(() => {
      reloadToday();
      reloadHealth();
    }, 60_000);
    return () => window.clearInterval(t);
  }, [reloadToday, reloadHealth]);

  const merged: Today | null = useMemo(() => (today.data ? { ...today.data, live: live.snapshot ?? today.data.live } : null), [today.data, live.snapshot]);
  const o = useMemo(
    () => (history.data && merged ? buildOverview(history.data, merged, health.data, range, yesterday, compare) : null),
    [history.data, merged, health.data, range, yesterday, compare],
  );

  const failed = history.error ?? today.error;
  return (
    <>
      <PageHead title="Overview" sub={longToday()} />
      <div className="filters">
        <Seg label="Range" items={RANGES} value={range} onChange={setRange} />
        <Chip icon="compare_arrows" on={compare} onClick={() => setCompare((v) => !v)}>
          Compare to previous {range === "today" ? "7" : range} days
        </Chip>
        <Chip on icon="check">
          Internal accounts hidden
        </Chip>
      </div>
      {failed && !o && <Panel><Failed error={failed} onRetry={() => { history.reload(); today.reload(); }} what="the overview" /></Panel>}
      {o && merged && (
        <>
          <div className="kpis">
            {o.kpis.map((k) => (
              <div className="kpi" key={k.label}>
                <span className="kk">{k.label}</span>
                <span className="kv num">{k.value}</span>
                <span className={`up${k.up ? "" : " down"}`}>
                  <Icon name={k.up ? "arrow_upward" : "arrow_downward"} />
                  {k.delta}
                </span>
                <Spark values={k.spark} />
                <span className="sub">{k.sub}</span>
              </div>
            ))}
            <Link className="kpi alert" href="/reports/">
              <span className="kk">Open reports</span>
              <span className="kv num">{num(o.open.count)}</span>
              {o.open.oldestAt ? (
                <span className={ageHours(o.open.oldestAt) >= 18 ? "warn" : "sub"} style={{ font: "500 12px/1 var(--font-ui)", display: "flex", gap: 4, alignItems: "center" }}>
                  <Icon name="schedule" />
                  Oldest {age(o.open.oldestAt)}
                </span>
              ) : (
                <span className="sub">None waiting</span>
              )}
              <span className="sub">Target: handled within 24 h</span>
              <span className="go" style={{ marginTop: "auto" }}>
                Open reports queue
                <Icon name="chevron_right" />
              </span>
            </Link>
          </div>

          <div className="grid">
            <SectionHead title="Live now" parts={o.sentences.live} />
            <Panel title="Running auctions" sub="Updates every 5 s while this tab is open" className="c8" loading={live.stale && live.snapshot != null}>
              <div className="stat3">
                {[
                  ["Auctions live", merged.live.now.liveAuctions, "sensors"],
                  ["Bids a minute", merged.live.now.bidsLastMinute, "gavel"],
                  ["Watching", merged.live.now.auctionViewers, "visibility"],
                ].map(([label, value, icon]) => (
                  <div className="lcard" key={label as string}>
                    <span className="sub">
                      <Icon name={icon as string} /> {label}
                    </span>
                    <span className="big">{num(value as number)}</span>
                  </div>
                ))}
              </div>
            </Panel>
            <Panel title="Activity" sub="Anonymised, last 50 events" className="c4">
              {merged.live.feed.length === 0 ? (
                <span className="sub">Nothing yet today.</span>
              ) : (
                <div className="feed" style={{ maxHeight: 300, overflow: "auto" }}>
                  {merged.live.feed.map((f, i) => (
                    <div className="fi" key={i}>
                      <span className="ft num">{age(f.at)} ago</span>
                      <div className="col">
                        <span className="fa">{FEED[f.type] ?? f.type}</span>
                        {f.district && <span className="sub">{f.district}</span>}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </Panel>

            <SectionHead title="Growth" parts={o.sentences.growth} />
            <ChartPanel
              title="New users a day"
              sub={`${day(o.range.from)} to ${day(o.range.to)}`}
              className="c7"
              loading={history.loading}
              table={{ head: ["Day", "New users", ...(o.newUsers.previous ? ["Previous period"] : [])], rows: o.newUsers.days.map((d, i) => [day(d), o.newUsers.values[i], ...(o.newUsers.previous ? [o.newUsers.previous[i] ?? 0] : [])]) }}
            >
              {o.newUsers.previous && <Legend items={[[SERIES[0], "New users"], ["var(--ink3)", "Same day, previous period"]]} />}
              <LineChart
                days={o.newUsers.days}
                series={[
                  { label: "New users", color: SERIES[0], values: o.newUsers.values },
                  ...(o.newUsers.previous ? [{ label: "Previous period", color: "var(--ink3)", values: o.newUsers.previous, dashed: true }] : []),
                ]}
              />
            </ChartPanel>
            <ChartPanel title="Sign-up funnel" sub={o.range.label === "today" ? "Last 7 days" : `Last ${o.range.days} days`} className="c5" loading={history.loading} table={{ head: ["Step", "People"], rows: o.funnel.map((f) => [f.key, f.value]) }}>
              <Funnel rows={o.funnel} />
            </ChartPanel>

            <SectionHead title="Engagement" parts={o.sentences.engagement} />
            <ChartPanel
              title="Active users"
              sub="Counted once per person per day"
              className="c7"
              loading={history.loading}
              table={{ head: ["Day", "Daily", "Weekly", "Monthly"], rows: o.active.days.map((d, i) => [day(d), o.active.dau[i], o.active.wau[i], o.active.mau[i]]) }}
            >
              <Legend items={[[SERIES[0], "Daily active"], [SERIES[1], "Weekly active"], [SERIES[2], "Monthly active"]]} />
              <LineChart
                days={o.active.days}
                width={500}
                endLabels
                series={[
                  { label: "Daily", color: SERIES[0], values: o.active.dau },
                  { label: "Weekly", color: SERIES[1], values: o.active.wau },
                  { label: "Monthly", color: SERIES[2], values: o.active.mau },
                ]}
              />
            </ChartPanel>
            <ChartPanel
              title="Retention by sign-up week"
              sub="Share of each week's new users who came back"
              className="c5"
              loading={history.loading}
              table={{ head: ["Week", ...[0, 1, 2, 3, 4, 5].map((w) => `Week ${w}`)], rows: o.retention.map((r) => [r.label, ...r.pct.slice(0, 6).map((p) => (p == null ? "—" : `${p}%`))]) }}
            >
              {o.retention.length ? <Heat cohorts={o.retention} /> : <span className="sub">No sign-up weeks yet.</span>}
            </ChartPanel>

            <SectionHead title="Leagues and auctions" parts={o.sentences.leagues} />
            <ChartPanel title="Leagues by status" sub={`Right now, ${num(o.status.reduce((s, r) => s + r.value, 0))} leagues`} className="c6" table={{ head: ["Status", "Leagues"], rows: o.status.map((s) => [s.key, s.value]) }}>
              <SegBar rows={o.status} colors={["#5FB294", "#F2A63B", "#2E9070", "#084C38"]} inks={["#0E1116", "#0E1116", "#FFF", "#FFF"]} />
            </ChartPanel>
            <ChartPanel title="Auctions completed a week" sub="Last 8 weeks" className="c6" loading={history.loading} table={{ head: ["Week of", "Auctions"], rows: o.auctionsWeekly.map((w) => [day(w.week), w.value]) }}>
              <Columns values={o.auctionsWeekly.map((w) => w.value)} labels={o.auctionsWeekly.map((w) => day(w.week))} x={[day(o.auctionsWeekly[0].week), day(o.auctionsWeekly[o.auctionsWeekly.length - 1].week)]} />
            </ChartPanel>
            <ChartPanel title="Users by district" sub="Top 5 and the rest" className="c6" table={{ head: ["District", "Users"], rows: o.districts.map((d) => [d.key, d.value]) }}>
              {o.districts.length ? <Ranked rows={o.districts} /> : <span className="sub">No profiles with a district yet.</span>}
            </ChartPanel>
            <Panel title="SMS codes today" sub="Daily cap set on the backend" className="c6">
              {health.data ? (
                <>
                  <div className="row" style={{ alignItems: "baseline" }}>
                    <span className="big">{num(health.data.sms.sentToday)}</span>
                    <span className="sub">of {num(health.data.sms.dailyCap)}</span>
                  </div>
                  <Meter value={health.data.sms.sentToday} max={health.data.sms.dailyCap} label="Resets at 05:30 India time (midnight UTC). At the cap, new sign-ins wait until then." />
                </>
              ) : (
                <span className="sub">Loading…</span>
              )}
            </Panel>

            <SectionHead title="Trust and safety" parts={o.sentences.trust} />
            <ChartPanel
              title="Reports opened and closed"
              sub="Last 14 days"
              className="c5"
              loading={history.loading}
              table={{ head: ["Day", "Opened", "Closed"], rows: o.reports14.days.map((d, i) => [day(d), o.reports14.opened[i], o.reports14.closed[i]]) }}
            >
              <Legend items={[[SERIES[2], "Opened"], [SERIES[0], "Closed"]]} />
              <Paired a={o.reports14.opened} b={o.reports14.closed} labels={o.reports14.days.map(day)} x={[day(o.reports14.days[0]), day(o.reports14.days[13])]} />
            </ChartPanel>
            <Panel title="Time to close" sub="Median, last 30 days" className="c3">
              <div className="row" style={{ alignItems: "baseline" }}>
                <span className="big">{o.medianCloseHours == null ? "—" : `${o.medianCloseHours} h`}</span>
                <span className="sub">target 24 h</span>
              </div>
              <Meter value={Math.min(24, o.medianCloseHours ?? 0)} max={24} label={`${num(merged.moderation.suspendedAccounts)} accounts suspended, ${num(merged.moderation.imagesRemoved30d)} images removed in 30 days.`} />
            </Panel>
            <ChartPanel title="Open by reason" sub={`${num(o.open.count)} open`} className="c4" table={{ head: ["Reason", "Open"], rows: o.openByReason.map((r) => [reasonLabel(r.key), r.value]) }}>
              {o.openByReason.length ? <Ranked rows={o.openByReason.map((r) => ({ key: reasonLabel(r.key), value: r.value }))} color={SERIES[2]} /> : <span className="sub">Nothing open.</span>}
            </ChartPanel>

            <div id="health" style={{ display: "contents" }}>
              <SectionHead title="System health" parts={o.sentences.health} />
            </div>
            <Panel title="Checks" sub="Refreshed every minute" className="c12">
              <div>
                {o.healthRows.map((h) => (
                  <div className="hrow" key={h.name}>
                    <Icon name={h.ok ? "check_circle" : "error"} className={h.ok ? "ok" : "warn"} />
                    <span className="strong">{h.name}</span>
                    <span style={{ color: "var(--ink2)" }}>{h.text}</span>
                  </div>
                ))}
              </div>
            </Panel>
          </div>
        </>
      )}
    </>
  );
}
