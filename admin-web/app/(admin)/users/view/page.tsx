"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { Calendar } from "@/components/charts";
import { Avatar, Banner, Btn, Failed, Kv, Panel, Status, Table, Tabs, Toast } from "@/components/ui";
import { ApiError, put, qs } from "@/lib/api";
import { actionLabel, actionTone, dateTime, day, fullDate, LEAGUE_STATUS, num, pts, reasonLabel, reasonTone, roleLabel, stamp, todayIst, USER_STATUS } from "@/lib/format";
import { useApi } from "@/lib/hooks";

type Tab = "profile" | "leagues" | "auction" | "activity" | "moderation" | "account";
type Row = Record<string, unknown>;

const TABS: [Tab, string][] = [
  ["profile", "Profile"],
  ["leagues", "Leagues"],
  ["auction", "Auction"],
  ["activity", "Activity"],
  ["moderation", "Moderation"],
  ["account", "Account"],
];

const BATTING: Record<string, string> = { RIGHT_HAND: "Right-handed", LEFT_HAND: "Left-handed" };

function UserView() {
  const id = useSearchParams().get("id") ?? "";
  const [tab, setTab] = useState<Tab>("profile");
  const profile = useApi<Row>(id ? `/api/v1/admin/users/${id}` : null);
  const [internal, setInternal] = useState<boolean | null>(null);
  const [toast, setToast] = useState<{ text: string; undo: boolean } | null>(null);
  const [problem, setProblem] = useState<string | null>(null);

  if (profile.error && !profile.data) return <Panel><Failed error={profile.error} onRetry={profile.reload} what="this person" /></Panel>;
  const p = profile.data;
  if (!p) return null;
  if (p.status === "deleted") return <Panel><span className="sub">This account was deleted on {fullDate(p.deletedAt as string)}.</span></Panel>;
  const isInternal = internal ?? (p.isInternal as boolean);
  const name = (p.name as string) ?? "No name yet";
  const status = USER_STATUS[p.status as string] ?? ["neutral", String(p.status)];

  const setFlag = async (value: boolean, showUndo = true) => {
    setProblem(null);
    try {
      await put(`/api/v1/admin/users/${id}/internal`, { internal: value });
      setInternal(value);
      setToast(showUndo ? { text: `${name} ${value ? "marked as internal" : "no longer internal"}.`, undo: true } : null);
    } catch (e) {
      setProblem(e instanceof ApiError ? e.detail : "Couldn't change the internal flag.");
    }
  };

  return (
    <>
      <span className="crumb">
        <Link href="/users/">Users</Link> /
      </span>
      <div className="panel" style={{ gap: 16, paddingBottom: 0 }}>
        <div className="row" style={{ gap: 16, flexWrap: "wrap" }}>
          <Avatar name={name} photo={p.photoRemoved ? null : (p.photoUrl as string | null)} big />
          <div className="col" style={{ flex: 1, gap: 6 }}>
            <div className="row" style={{ gap: 10, flexWrap: "wrap" }}>
              <h1 style={{ margin: 0, font: "700 24px/1.2 var(--font-head)", letterSpacing: "-.02em" }}>{name}</h1>
              <Status tone={status[0]}>{status[1]}</Status>
              {isInternal && <Status tone="internal">Internal</Status>}
            </div>
            <span className="sub">
              {[roleLabel(p.playingRole as string | null), p.district].filter((s) => s && s !== "—").join(", ")} · joined {fullDate(p.createdAt as string)} · ID <span className="num">{id}</span>
            </span>
          </div>
          <div className="row" style={{ gap: 10 }}>
            <button type="button" className={`toggle${isInternal ? " on" : ""}`} role="switch" aria-checked={isInternal} aria-label="Internal account" onClick={() => setFlag(!isInternal)}>
              <i />
            </button>
            <span style={{ font: "500 13px var(--font-ui)", color: "var(--ink2)" }}>Internal account</span>
          </div>
          <Btn icon="content_copy" kind="ghost" onClick={() => navigator.clipboard?.writeText(id)}>
            Copy ID
          </Btn>
        </div>
        <Tabs items={TABS} value={tab} onChange={setTab} />
      </div>
      {problem && <Banner tone="danger" icon="cancel">{problem}</Banner>}
      {tab === "profile" && <ProfileTab p={p} />}
      {tab === "leagues" && <LeaguesTab id={id} />}
      {tab === "auction" && <AuctionTab id={id} />}
      {tab === "activity" && <ActivityTab id={id} />}
      {tab === "moderation" && <ModerationTab id={id} name={name} />}
      {tab === "account" && <AccountTab id={id} />}
      {toast && (
        <Toast action={toast.undo ? "Undo" : undefined} onAction={() => setFlag(!isInternal, false)} onDone={() => setToast(null)}>
          {toast.text}
        </Toast>
      )}
    </>
  );
}

function ProfileTab({ p }: { p: Row }) {
  return (
    <div className="grid">
      <div className="c7">
        <Panel title="Profile">
          <Kv
            pairs={[
              ["Name", (p.name as string) ?? "—"],
              ["Photo", p.photoRemoved ? <Status tone="danger">Removed by the team</Status> : p.photoUrl ? <Avatar name={(p.name as string) ?? ""} photo={p.photoUrl as string} big /> : "None"],
              ["Playing role", roleLabel(p.playingRole as string | null)],
              ["Batting", BATTING[p.battingStyle as string] ?? "—"],
              ["Bowling", p.bowlingStyle ? String(p.bowlingStyle).replace(/_/g, " ").toLowerCase() : "—"],
              ["State, district", [p.state, p.district].filter(Boolean).join(", ") || "—"],
              ["Phone number", <span className="sub" key="ph">Never shown. Use Find by phone number.</span>],
            ]}
          />
        </Panel>
      </div>
      <div className="c5">
        <Panel title="Sign-up">
          <Kv
            pairs={[
              ["Joined", dateTime(p.createdAt as string)],
              ["Terms accepted", p.termsVersion ? `Version ${p.termsVersion}, ${fullDate(p.termsAcceptedAt as string)}` : "—"],
              ["Last active", p.lastActive ? (p.lastActive === todayIst() ? "Today" : day(p.lastActive as string)) : "—"],
            ]}
          />
        </Panel>
      </div>
    </div>
  );
}

const LEAGUE_GROUPS: [string, string][] = [
  ["organizes", "Organizes"],
  ["coOrganizes", "Co-organizes"],
  ["owns", "Owns a franchise"],
  ["plays", "Plays"],
  ["follows", "Follows"],
];

function LeaguesTab({ id }: { id: string }) {
  const data = useApi<Record<string, Row[]>>(`/api/v1/admin/users/${id}/leagues`);
  if (!data.data) return data.error ? <Panel><Failed error={data.error} onRetry={data.reload} /></Panel> : null;
  return (
    <div className="grid">
      {LEAGUE_GROUPS.map(([key, title]) => (
        <Panel key={key} title={title} sub={`${num(data.data![key].length)}`} className="c6">
          {data.data![key].length === 0 ? (
            <span className="sub">None</span>
          ) : (
            <Table<Row>
              rows={data.data![key]}
              rowKey={(r) => `${r.leagueId}${r.at}`}
              columns={[
                { title: "League", render: (r) => <Link href={`/leagues/view/${qs({ id: r.leagueId as string })}`}>{r.name as string}</Link> },
                { title: "Status", render: (r) => <Status tone={(LEAGUE_STATUS[r.status as string] ?? ["neutral"])[0]}>{(LEAGUE_STATUS[r.status as string] ?? ["", r.status])[1] as string}</Status> },
                { title: "Since", render: (r) => <span className="num">{fullDate(r.at as string)}</span> },
                { title: "", render: (r) => <span className="sub">{(r.detail as string) ?? ""}</span> },
              ]}
            />
          )}
        </Panel>
      ))}
    </div>
  );
}

function AuctionTab({ id }: { id: string }) {
  const data = useApi<{ boughtAsOwner: Row[]; ownSales: Row[] }>(`/api/v1/admin/users/${id}/auction`);
  if (!data.data) return data.error ? <Panel><Failed error={data.error} onRetry={data.reload} /></Panel> : null;
  return (
    <div className="grid">
      <Panel title="Bought as franchise owner" className="c6">
        {data.data.boughtAsOwner.length === 0 ? <span className="sub">None</span> : (
          <Table<Row> rows={data.data.boughtAsOwner} rowKey={(r) => `${r.league}${r.player}`} columns={[
            { title: "League", render: (r) => r.league as string },
            { title: "Player", render: (r) => r.player as string },
            { title: "Price", right: true, render: (r) => <span className="num">{pts(r.price as number)}</span> },
          ]} />
        )}
      </Panel>
      <Panel title="Their own sale as a player" className="c6">
        {data.data.ownSales.length === 0 ? <span className="sub">Not auctioned yet</span> : (
          <Table<Row> rows={data.data.ownSales} rowKey={(r) => r.league as string} columns={[
            { title: "League", render: (r) => r.league as string },
            { title: "Result", render: (r) => String(r.outcome).toLowerCase() },
            { title: "Price", right: true, render: (r) => <span className="num">{pts(r.price as number | null)}</span> },
            { title: "Bought by", render: (r) => (r.franchise as string) ?? "—" },
          ]} />
        )}
      </Panel>
    </div>
  );
}

function ActivityTab({ id }: { id: string }) {
  const data = useApi<string[]>(`/api/v1/admin/users/${id}/activity`);
  if (!data.data) return data.error ? <Panel><Failed error={data.error} onRetry={data.reload} /></Panel> : null;
  const days = new Set(data.data);
  return (
    <div className="grid">
      <div className="c8">
        <Panel title="Days active" sub="Last 26 weeks, one square per day">
          <Calendar activeDays={days} today={todayIst()} />
        </Panel>
      </div>
      <div className="c4">
        <Panel title="In numbers">
          <Kv pairs={[["Days active", num(data.data.length)], ["First recorded", data.data[0] ? day(data.data[0]) : "—"], ["Last", data.data.length ? day(data.data[data.data.length - 1]) : "—"]]} />
          <span className="sub">Only the date is kept, never what they did. Days older than 13 months are deleted.</span>
        </Panel>
      </div>
    </div>
  );
}

function ModerationTab({ id, name }: { id: string; name: string }) {
  const data = useApi<{ reportsAbout: Row[]; reportsFiled: Row[]; actions: Row[]; notices: Row[]; blocks: { blockedByThem: number; blockingThem: number } }>(`/api/v1/admin/users/${id}/moderation`);
  if (!data.data) return data.error ? <Panel><Failed error={data.error} onRetry={data.reload} /></Panel> : null;
  const d = data.data;
  const reports = (rows: Row[]) =>
    rows.length === 0 ? <span className="sub">None</span> : (
      <Table<Row> rows={rows} rowKey={(r) => r.id as string} columns={[
        { title: "Report", render: (r) => <Link href={`/reports/view/${qs({ id: r.id as string })}`}>{(r.id as string).slice(0, 8)}</Link> },
        { title: "Reason", render: (r) => <Status tone={reasonTone(r.reason as string)}>{reasonLabel(r.reason as string)}</Status> },
        { title: "Status", render: (r) => <Status tone={r.status === "OPEN" ? "warn" : "neutral"}>{String(r.status).toLowerCase()}</Status> },
        { title: "Received", render: (r) => <span className="num">{fullDate(r.createdAt as string)}</span> },
      ]} />
    );
  const open = d.reportsAbout.filter((r) => r.status === "OPEN").length;
  return (
    <div className="grid">
      <div className="c7 col" style={{ gap: 20 }}>
        <Panel title={`Reports about ${name.split(" ")[0]}`} sub={`${open} open`}>{reports(d.reportsAbout)}</Panel>
        <Panel title={`Reports ${name.split(" ")[0]} filed`}>{reports(d.reportsFiled)}</Panel>
      </div>
      <div className="c5 col" style={{ gap: 20 }}>
        <Panel title="Actions and notices">
          {d.actions.length === 0 && d.notices.length === 0 ? <span className="sub">No actions taken. No notices sent.</span> : (
            <div className="tl">
              {d.actions.map((a, i) => (
                <div className="tli" key={`a${i}`}>
                  <span className="sub num">{stamp(a.at as string)}</span>
                  <i className="dot" />
                  <span><Status tone={actionTone(a.action as string)}>{actionLabel(a.action as string)}</Status> {a.note as string}</span>
                </div>
              ))}
              {d.notices.map((n, i) => (
                <div className="tli" key={`n${i}`}>
                  <span className="sub num">{stamp(n.at as string)}</span>
                  <i className="dot" />
                  <span>Notice: {String(n.kind).replace(/_/g, " ").toLowerCase()} removed{n.seenAt ? ", seen" : ""}</span>
                </div>
              ))}
            </div>
          )}
        </Panel>
        <Panel title="Blocks" sub="Counts only: who blocked whom stays private">
          <Kv pairs={[["They blocked", num(d.blocks.blockedByThem)], ["Blocked by others", num(d.blocks.blockingThem)]]} />
        </Panel>
      </div>
    </div>
  );
}

function AccountTab({ id }: { id: string }) {
  const data = useApi<Row>(`/api/v1/admin/users/${id}/account`);
  if (!data.data) return data.error ? <Panel><Failed error={data.error} onRetry={data.reload} /></Panel> : null;
  const a = data.data;
  return (
    <div className="grid">
      <div className="c7">
        <Panel title="Account">
          <Kv
            pairs={[
              ["Suspended", a.suspendedAt ? `Since ${dateTime(a.suspendedAt as string)}` : "No"],
              ["Deletion", a.deletionRequestedAt ? `Requested ${fullDate(a.deletionRequestedAt as string)}, erased 14 days later` : "Not requested"],
              ["Devices for notifications", <span key="d"><span className="num">{num(a.pushDevices as number)}</span> (tokens never shown)</span>],
              ["App sessions", `Signed in on ${num(a.appSessions as number)} device${a.appSessions === 1 ? "" : "s"}`],
            ]}
          />
        </Panel>
      </div>
      <div className="c5">
        <Banner tone="neutral" icon="info">
          Marking an account <b>internal</b> removes it and its leagues from every count and chart. Use it for your own numbers, the Play reviewer and test accounts. It changes nothing in the app.
        </Banner>
      </div>
    </div>
  );
}

export default function UserViewPage() {
  return (
    <Suspense>
      <UserView />
    </Suspense>
  );
}
