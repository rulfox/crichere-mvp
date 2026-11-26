"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState, type ReactNode } from "react";
import { Banner, Btn, Dialog, Empty, Failed, Kv, LeagueSquare, Panel, PersonCell, Status, Table, Tabs } from "@/components/ui";
import { ApiError, post, qs } from "@/lib/api";
import { actionLabel, actionTone, dateTime, day, fullDate, LEAGUE_STATUS, num, pts, reasonLabel, reasonTone, roleLabel, rupees, stamp } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { Fee, PaymentProof, PersonRef } from "@/lib/types";

type Tab = "overview" | "franchises" | "players" | "auction" | "awards" | "moderation" | "timeline";
type Row = Record<string, unknown>;

const TABS: [Tab, string][] = [
  ["overview", "Overview"],
  ["franchises", "Franchises"],
  ["players", "Players"],
  ["auction", "Auction"],
  ["awards", "Awards"],
  ["moderation", "Moderation"],
  ["timeline", "Timeline"],
];

const TIMELINE: Record<string, string> = {
  LEAGUE_CREATED: "League created",
  LEAGUE_UPDATED: "League details last edited",
  AUCTION_SCHEDULED_FOR: "Auction scheduled for this time",
  FIRST_BID: "First bid in the auction",
  AUCTION_COMPLETED: "Auction completed",
  LEAGUE_COMPLETED: "League marked completed",
  ORGANIZER_HANDOVER: "Organizer handed over",
  CO_ORGANIZER_ADDED: "Co-organizer added",
  FRANCHISE_JOINED: "Franchise joined",
  PLAYER_JOINED: "Player joined",
  PLAYER_LEFT_OR_REMOVED: "Player left or was removed",
};

const feeStatus = (fee: Fee) =>
  fee.state === "NO_FEE" ? <span className="sub">No fee</span> : fee.state === "PROOF_UPLOADED" ? <Status tone="ok">Proof uploaded</Status> : <Status tone="warn">Waiting for proof</Status>;

function LeagueView() {
  const id = useSearchParams().get("id") ?? "";
  const [tab, setTab] = useState<Tab>("overview");
  const overview = useApi<Row>(id ? `/api/v1/admin/leagues/${id}` : null);
  if (overview.error && !overview.data) return <Panel><Failed error={overview.error} onRetry={overview.reload} what="this league" /></Panel>;
  const l = overview.data;
  if (!l) return null;
  const status = LEAGUE_STATUS[l.status as string] ?? ["neutral", String(l.status)];
  const organizer = l.organizer as PersonRef;

  return (
    <>
      <span className="crumb">
        <Link href="/leagues/">Leagues</Link> /
      </span>
      <div className="panel" style={{ padding: 0, gap: 0, overflow: "hidden" }}>
        <div className="imgph" style={{ height: 150, borderRadius: 0 }}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          {l.bannerUrl && !l.bannerRemoved ? <img src={l.bannerUrl as string} alt="" style={{ objectFit: "cover" }} /> : l.bannerRemoved ? "Banner removed by the team" : "No banner"}
        </div>
        <div style={{ display: "flex", gap: 16, alignItems: "flex-end", padding: "0 22px 18px", marginTop: -32 }}>
          <LeagueSquare name={l.name as string} logo={l.logoRemoved ? null : (l.logoUrl as string | null)} big />
          <div className="col" style={{ flex: 1, gap: 6, paddingTop: 40 }}>
            <div className="row" style={{ gap: 10, flexWrap: "wrap" }}>
              <h1 style={{ margin: 0, font: "700 24px/1.2 var(--font-head)", letterSpacing: "-.02em" }}>{l.name as string}</h1>
              <Status tone={status[0] as never}>{status[1]}</Status>
            </div>
            <span className="sub">
              {l.district as string}, {l.state as string} · organizer {organizer.name} · ID <span className="num">{id}</span>
            </span>
          </div>
          <Btn icon="content_copy" kind="ghost" onClick={() => navigator.clipboard?.writeText(id)}>
            Copy ID
          </Btn>
        </div>
        <div style={{ padding: "0 22px" }}>
          <Tabs items={TABS} value={tab} onChange={setTab} />
        </div>
      </div>
      {tab === "overview" && <OverviewTab l={l} />}
      {tab === "franchises" && <FranchisesTab id={id} />}
      {tab === "players" && <PlayersTab id={id} />}
      {tab === "auction" && <AuctionTab id={id} />}
      {tab === "awards" && <AwardsTab id={id} />}
      {tab === "moderation" && <ModerationTab id={id} />}
      {tab === "timeline" && <TimelineTab id={id} />}
    </>
  );
}

function OverviewTab({ l }: { l: Row }) {
  const co = (l.coOrganizers as { person: PersonRef; grantedAt: string; revokedAt: string | null }[]) ?? [];
  const organizer = l.organizer as PersonRef;
  return (
    <div className="grid">
      <div className="c7">
        <Panel title="Details">
          <Kv
            pairs={[
              ["Ground", l.groundName ? <>{l.groundName as string}{l.latitude != null && <> · <a href={`https://maps.google.com/?q=${l.latitude},${l.longitude}`} target="_blank" rel="noopener noreferrer">map pin</a></>}</> : "—"],
              ["Starts", day(l.startsOn as string)],
              ["Format", (l.format as string) ?? "—"],
              ["Required", `${l.franchisesRequired ?? "—"} franchises, ${l.playersRequired ?? "—"} players`],
              ["Player fee", rupees(l.playerFee as number | null)],
              ["Franchise fee", rupees(l.franchiseFee as number | null)],
              ["Organizer UPI ID", (l.organizerUpiId as string) ?? "—"],
              ["Description", (l.description as string) ?? "—"],
              ["Followers", num(l.followers as number)],
              ["Created", dateTime(l.createdAt as string)],
              ["Updated", dateTime(l.updatedAt as string)],
              ["Completed", dateTime(l.completedAt as string | null)],
            ]}
          />
        </Panel>
      </div>
      <div className="c5 col" style={{ gap: 20 }}>
        <Panel title="People">
          <Link href={`/users/view/${qs({ id: organizer.id ?? "" })}`}>
            <PersonCell name={organizer.name} sub="Organizer" />
          </Link>
          {co.map((c) => (
            <Link key={`${c.person.id}${c.grantedAt}`} href={`/users/view/${qs({ id: c.person.id ?? "" })}`}>
              <PersonCell name={c.person.name} sub={`Co-organizer since ${fullDate(c.grantedAt)}${c.revokedAt ? `, removed ${fullDate(c.revokedAt)}` : ""}`} />
            </Link>
          ))}
          {l.organizerHandoverAt != null && <span className="sub">Organizer handed over on {fullDate(l.organizerHandoverAt as string)}.</span>}
        </Panel>
        <Panel title="Images">
          <Kv
            pairs={[
              ["Logo", l.logoRemoved ? <Status tone="danger">Removed by the team</Status> : l.logoUrl ? "Set" : "None"],
              ["Banner", l.bannerRemoved ? <Status tone="danger">Removed by the team</Status> : l.bannerUrl ? "Set" : "None"],
            ]}
          />
        </Panel>
      </div>
    </div>
  );
}

function TabTable<T extends Row>({ path, empty, render }: { path: string; empty: string; render: (rows: T[]) => ReactNode }) {
  const data = useApi<T[]>(path);
  if (data.error && !data.data) return <div className="tablewrap"><Failed error={data.error} onRetry={data.reload} /></div>;
  if (!data.data) return null;
  if (data.data.length === 0) return <div className="tablewrap"><Empty icon="inbox" title={empty} /></div>;
  return <div className="tablewrap">{render(data.data)}</div>;
}

function FranchisesTab({ id }: { id: string }) {
  const [proof, setProof] = useState<string | null>(null);
  return (
    <>
      <TabTable<Row>
        path={`/api/v1/admin/leagues/${id}/franchises`}
        empty="No franchises yet"
        render={(rows) => (
          <Table<Row>
            rows={rows}
            rowKey={(r) => r.id as string}
            columns={[
              { title: "Franchise", render: (r) => <div className="cell"><LeagueSquare name={r.name as string} logo={r.logoUrl as string | null} /><div className="col"><span className="strong">{r.name as string}</span><span className="sub">Owner {(r.owner as PersonRef).name}</span></div></div> },
              { title: "Joined", render: (r) => <span className="num">{fullDate(r.joinedAt as string)}</span> },
              { title: "Fee", render: (r) => <span className="row">{feeStatus(r.fee as Fee)}{(r.fee as Fee).state === "PROOF_UPLOADED" && <a className="sub" onClick={() => setProof(r.id as string)}>View</a>}</span> },
              { title: "Players bought", right: true, render: (r) => <span className="num">{r.playersBought as number}</span> },
              { title: "Purse used", right: true, render: (r) => <span className="num">{pts(r.purseUsed as number)}</span> },
              { title: "Purse left", right: true, render: (r) => <span className="num">{pts(r.purseLeft as number | null)}</span> },
              { title: "Membership", render: (r) => (r.removedAt ? <Status tone="neutral">Removed</Status> : r.leaveRequestedAt ? <Status tone="warn">Asked to leave</Status> : <Status tone="ok">Active</Status>) },
            ]}
          />
        )}
      />
      {proof && <ProofDialog kind="franchise" rowId={proof} onClose={() => setProof(null)} />}
    </>
  );
}

function PlayersTab({ id }: { id: string }) {
  const [proof, setProof] = useState<string | null>(null);
  return (
    <>
      <TabTable<Row>
        path={`/api/v1/admin/leagues/${id}/players`}
        empty="No players yet"
        render={(rows) => (
          <Table<Row>
            rows={rows}
            rowKey={(r) => r.id as string}
            columns={[
              { title: "Player", render: (r) => <PersonCell name={(r.person as PersonRef).name} sub={roleLabel(r.playingRole as string | null)} /> },
              { title: "Joined", render: (r) => <span className="num">{fullDate(r.joinedAt as string)}</span> },
              { title: "Fee", render: (r) => <span className="row">{feeStatus(r.fee as Fee)}{(r.fee as Fee).state === "PROOF_UPLOADED" && <a className="sub" onClick={() => setProof(r.id as string)}>View</a>}</span> },
              { title: "Membership", render: (r) => (r.removedAt ? <Status tone="neutral">Left or removed</Status> : r.leaveRequestedAt ? <Status tone="warn">Asked to leave</Status> : <Status tone="ok">Active</Status>) },
              {
                title: "Auction result",
                render: (r) =>
                  r.auctionOutcome === "SOLD" ? (
                    <span><span className="num">{pts(r.soldPrice as number)}</span> to {r.boughtBy as string}</span>
                  ) : r.auctionOutcome ? (
                    <Status tone="neutral">{String(r.auctionOutcome).toLowerCase()}</Status>
                  ) : (
                    <span className="sub">Not auctioned yet</span>
                  ),
              },
            ]}
          />
        )}
      />
      {proof && <ProofDialog kind="player" rowId={proof} onClose={() => setProof(null)} />}
    </>
  );
}

/** Board L4: opening a proof writes an audit row, so it happens only on an explicit click. */
function ProofDialog({ kind, rowId, onClose }: { kind: "player" | "franchise"; rowId: string; onClose: () => void }) {
  const [proof, setProof] = useState<PaymentProof | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [opened, setOpened] = useState(false);
  const open = async () => {
    setOpened(true);
    try {
      setProof(await post<PaymentProof>(`/api/v1/admin/payment-proofs/${kind}/${rowId}`));
    } catch (e) {
      setError(e instanceof ApiError ? e.detail : "Couldn't open the proof.");
    }
  };
  return (
    <Dialog title={`Payment proof${proof ? `: ${proof.payer.name}` : ""}`} width={620} onClose={onClose} actions={<Btn onClick={onClose}>Close</Btn>}>
      <Banner tone="info" icon="visibility">
        Opening a payment proof is <b>recorded in the audit log</b> with your email and the time.
      </Banner>
      {!opened && <Btn kind="primary" icon="visibility" onClick={open}>Open the proof</Btn>}
      {error && <Banner tone="danger" icon="cancel">{error}</Banner>}
      {proof && (
        <div style={{ display: "grid", gridTemplateColumns: "200px minmax(0, 1fr)", gap: 18 }}>
          <a className="imgph" style={{ height: 320 }} href={proof.url ?? undefined} target="_blank" rel="noopener noreferrer">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            {proof.url ? <img src={proof.url} alt="Payment screenshot as uploaded" /> : "Not available"}
          </a>
          <Kv pairs={[["Payer", proof.payer.name], ["Fee", `${rupees(proof.fee)}, ${kind} fee`], ["Joined", dateTime(proof.uploadedAt)]]} />
        </div>
      )}
    </Dialog>
  );
}

/**
 * The league's bid steps (docs/PHASE22.md) from the raw `auction_bid_ranges` column (`1000:250,3000:500`)
 * plus the step above them: "250 / 500, then 2,500 pts". No ranges: just the one step.
 */
function bidSteps(ranges: string | null, above: number | null): string {
  if (above == null) return "—";
  const steps = (ranges ?? "").split(",").filter(Boolean).map((pair) => Number(pair.split(":")[1]));
  return steps.length === 0 ? pts(above) : `${steps.map((n) => num(n)).join(" / ")}, then ${pts(above)}`;
}

function AuctionTab({ id }: { id: string }) {
  const data = useApi<Row>(`/api/v1/admin/leagues/${id}/auction`);
  if (data.error && !data.data) return <Panel><Failed error={data.error} onRetry={data.reload} /></Panel>;
  const a = data.data;
  if (!a) return null;
  const bids = (a.bids as { playerId: string; amount: number; at: string; franchise: string | null }[]) ?? [];
  return (
    <>
      <div className="kpis" style={{ gridTemplateColumns: "repeat(5, minmax(0, 1fr))" }}>
        {[
          ["Base price", pts(a.auctionBasePrice as number | null)],
          ["Purse", pts(a.auctionPurse as number | null)],
          ["Squad size", a.auctionSquadMin != null ? `${a.auctionSquadMin} to ${a.auctionSquadMax}` : "—"],
          ["Bid steps", bidSteps(a.auctionBidRanges as string | null, a.auctionBidIncrement as number | null)],
          ["Status", String(a.auctionStatus).replace("_", " ").toLowerCase()],
        ].map(([k, v]) => (
          <div className="kpi" key={k}>
            <span className="kk">{k}</span>
            <span className="num" style={{ fontSize: 18, fontWeight: 600 }}>{v}</span>
          </div>
        ))}
      </div>
      <Panel title="Bid history" sub={`${num(bids.length)} bids, newest first${bids.length >= 2000 ? " (latest 2,000)" : ""}`}>
        {bids.length === 0 ? (
          <span className="sub">No bids yet.</span>
        ) : (
          <div className="tl" style={{ maxHeight: 420, overflow: "auto" }}>
            {bids.map((b, i) => (
              <div className="tli" key={i}>
                <span className="sub num">{stamp(b.at)}</span>
                <i className="dot" />
                <span>
                  {b.franchise ?? "Removed franchise"} <span className="num strong">{pts(b.amount)}</span>
                </span>
              </div>
            ))}
          </div>
        )}
      </Panel>
    </>
  );
}

function AwardsTab({ id }: { id: string }) {
  return (
    <TabTable<Row>
      path={`/api/v1/admin/leagues/${id}/awards`}
      empty="No awards"
      render={(rows) => (
        <Table<Row>
          rows={rows}
          rowKey={(r) => r.name as string}
          columns={[
            { title: "Award", render: (r) => r.name as string },
            { title: "Cash", right: true, render: (r) => <span className="num">{rupees(r.cashAmount as number | null)}</span> },
            { title: "Trophy", render: (r) => (r.hasTrophy ? "Yes" : "No") },
          ]}
        />
      )}
    />
  );
}

function ModerationTab({ id }: { id: string }) {
  const data = useApi<{ reports: Row[]; actions: Row[] }>(`/api/v1/admin/leagues/${id}/moderation`);
  if (!data.data) return data.error ? <Panel><Failed error={data.error} onRetry={data.reload} /></Panel> : null;
  return (
    <div className="grid">
      <div className="c7">
        <Panel title="Reports on this league and its people">
          {data.data.reports.length === 0 ? (
            <span className="sub">None</span>
          ) : (
            <Table<Row>
              rows={data.data.reports}
              rowKey={(r) => r.id as string}
              columns={[
                { title: "Report", render: (r) => <Link href={`/reports/view/${qs({ id: r.id as string })}`}>{(r.id as string).slice(0, 8)}</Link> },
                { title: "Reason", render: (r) => <Status tone={reasonTone(r.reason as string)}>{reasonLabel(r.reason as string)}</Status> },
                { title: "Status", render: (r) => <Status tone={r.status === "OPEN" ? "warn" : "neutral"}>{String(r.status).toLowerCase()}</Status> },
                { title: "Received", render: (r) => <span className="num">{fullDate(r.createdAt as string)}</span> },
              ]}
            />
          )}
        </Panel>
      </div>
      <div className="c5">
        <Panel title="Actions taken">
          {data.data.actions.length === 0 ? (
            <span className="sub">No actions taken.</span>
          ) : (
            <div className="tl">
              {data.data.actions.map((a, i) => (
                <div className="tli" key={i}>
                  <span className="sub num">{stamp(a.at as string)}</span>
                  <i className="dot" />
                  <span><Status tone={actionTone(a.action as string)}>{actionLabel(a.action as string)}</Status> {a.note as string}</span>
                </div>
              ))}
            </div>
          )}
        </Panel>
      </div>
    </div>
  );
}

function TimelineTab({ id }: { id: string }) {
  const data = useApi<{ at: string; kind: string }[]>(`/api/v1/admin/leagues/${id}/timeline`);
  if (!data.data) return data.error ? <Panel><Failed error={data.error} onRetry={data.reload} /></Panel> : null;
  return (
    <Panel>
      <div className="tl">
        {data.data.map((t, i) => (
          <div className="tli" key={i}>
            <span className="sub num">{stamp(t.at)}</span>
            <i className={`dot${t.kind.startsWith("AUCTION") || t.kind === "FIRST_BID" ? " w" : t.kind === "LEAGUE_CREATED" ? " b" : ""}`} />
            <span>{TIMELINE[t.kind] ?? t.kind}</span>
          </div>
        ))}
      </div>
      <span className="sub">Built from stored timestamps only; nothing extra is tracked.</span>
    </Panel>
  );
}

export default function LeagueViewPage() {
  return (
    <Suspense>
      <LeagueView />
    </Suspense>
  );
}
