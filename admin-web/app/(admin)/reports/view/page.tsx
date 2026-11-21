"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { PageHead } from "@/components/Shell";
import { Avatar, Banner, Btn, Dialog, Failed, Field, Icon, Kv, NoteBox, Panel, PersonCell, Status, Toast } from "@/components/ui";
import { ApiError, post, qs } from "@/lib/api";
import { actionLabel, age, dateTime, fullDate, num, reasonLabel, stamp } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { ReportDetail } from "@/lib/types";

type ImageKind = "PHOTO" | "LEAGUE_LOGO" | "LEAGUE_BANNER";
type Pending = null | "suspend" | "guard" | { remove: ImageKind };

const IMAGE_LABEL: Record<ImageKind, string> = { PHOTO: "profile photo", LEAGUE_LOGO: "league logo", LEAGUE_BANNER: "league banner" };

function ReportView() {
  const id = useSearchParams().get("id") ?? "";
  const router = useRouter();
  const report = useApi<ReportDetail>(id ? `/api/v1/admin/reports/${id}` : null);
  const [note, setNote] = useState("");
  const [pending, setPending] = useState<Pending>(null);
  const [urgent, setUrgent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  if (report.error && !report.data) return <Panel><Failed error={report.error} onRetry={report.reload} what="this report" /></Panel>;
  const r = report.data;
  if (!r) return null;

  const open = r.status === "OPEN";
  const user = r.target.kind === "USER";
  const suspendTarget = user ? r.target.id : r.target.organizerId;
  const suspendName = user ? r.target.name : r.target.organizerName;
  const ev = r.evidence as Record<string, string | null>;
  const images: ImageKind[] = user ? (ev.photoUrl ? ["PHOTO"] : []) : ([ev.logoUrl ? "LEAGUE_LOGO" : null, ev.bannerUrl ? "LEAGUE_BANNER" : null].filter(Boolean) as ImageKind[]);
  const evidenceImage = user ? ev.photoUrl : (ev.logoUrl ?? ev.bannerUrl);
  const noteOk = note.trim().length > 0;

  const act = async (fn: () => Promise<unknown>, done: string) => {
    setBusy(true);
    setProblem(null);
    try {
      await fn();
      setPending(null);
      setUrgent(false);
      setNote("");
      setToast(done);
      report.reload();
    } catch (e) {
      if (e instanceof ApiError && e.code === "ORGANIZER_OF_LIVE_AUCTION") setPending("guard");
      else setProblem(e instanceof ApiError ? e.detail : "Something went wrong. Nothing was changed.");
    } finally {
      setBusy(false);
    }
  };

  const close = (kind: "dismiss" | "resolve") => act(() => post(`/api/v1/admin/reports/${r.id}/${kind}`, { note: note.trim() }), `Report ${kind === "dismiss" ? "dismissed" : "resolved"}.`);
  const suspend = (duringAuction: boolean) =>
    act(() => post(`/api/v1/admin/users/${suspendTarget}/suspend`, { note: note.trim(), reportId: r.id, duringAuction }), `${suspendName ?? "Account"} suspended.`);
  const remove = (kind: ImageKind) =>
    act(() => post(`/api/v1/admin/images/remove`, { kind, targetId: r.target.id, reportId: r.id, note: note.trim() }), `${IMAGE_LABEL[kind][0].toUpperCase()}${IMAGE_LABEL[kind].slice(1)} removed.`);

  return (
    <>
      <PageHead
        crumb={
          <>
            <Link href="/reports/">Reports</Link> / {r.id.slice(0, 8)}
          </>
        }
        title={reasonLabel(r.reason)}
        sub={`${user ? "Person" : "League"}: ${r.target.name ?? "deleted"}, received ${age(r.createdAt)} ago${open ? "" : `, ${r.status.toLowerCase()}`}`}
        actions={
          open && (
            <>
              <Btn icon="close" disabled={!noteOk || busy} onClick={() => close("dismiss")} title={noteOk ? undefined : "Write a decision note first"}>
                Dismiss
              </Btn>
              <Btn icon="check" disabled={!noteOk || busy} onClick={() => close("resolve")}>
                Resolve
              </Btn>
              {images.map((k) => (
                <Btn key={k} kind="dangero" icon="hide_image" disabled={busy} onClick={() => setPending({ remove: k })}>
                  Remove {IMAGE_LABEL[k].replace("profile ", "").replace("league ", "")}
                </Btn>
              ))}
              {suspendTarget && !r.target.suspended && (
                <Btn kind="danger" icon="block" disabled={busy} onClick={() => setPending("suspend")}>
                  {user ? "Suspend" : "Suspend organizer"}
                </Btn>
              )}
            </>
          )
        }
      />
      {problem && (
        <Banner tone="danger" icon="cancel">
          {problem}
        </Banner>
      )}
      <div className="grid">
        <div className="c8 col" style={{ gap: 20 }}>
          <Panel title="Evidence" sub="As it was when reported">
            <div style={{ display: "grid", gridTemplateColumns: "220px minmax(0, 1fr)", gap: 20 }}>
              <div className="imgph" style={{ height: 220 }}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                {evidenceImage ? <img src={evidenceImage} alt="Reported image" /> : "No image"}
              </div>
              <div className="col" style={{ gap: 12 }}>
                <Kv
                  pairs={[
                    ["Name at the time", ev.name ?? "—"],
                    ...(user ? [] : ([["Description", ev.description ?? "—"]] as [string, string][])),
                    ["Received", dateTime(r.createdAt)],
                    ...(r.contextLeagueId ? ([["Seen in league", <Link key="l" href={`/leagues/view/${qs({ id: r.contextLeagueId })}`}>Open league</Link>]] as [string, React.ReactNode][]) : []),
                  ]}
                />
                <span className="sub">
                  <Icon name="lock" className="muted" /> Saved when the report was made, so later edits can&apos;t hide it. Kept until the report closes.
                </span>
              </div>
            </div>
          </Panel>
          <Panel title="Reporter's note">
            <p className="say" style={{ color: "var(--ink)" }}>
              {r.details ? `“${r.details}”` : "No note."}
            </p>
          </Panel>
          {open && (
            <Panel title="Decision note" sub="Required for every action. Stored in the audit log with your email.">
              <NoteBox value={note} onChange={setNote} placeholder="Write what you saw and why you chose this action" />
            </Panel>
          )}
        </div>
        <div className="c4 col" style={{ gap: 20 }}>
          <Panel title={user ? "Reported person" : "Reported league"}>
            <div className="cell">
              <Avatar name={r.target.name ?? "Deleted"} big neutral={r.target.deleted} />
              <div className="col">
                <span className="strong" style={{ fontSize: 16 }}>
                  {r.target.name ?? "Deleted"}
                </span>
                <span className="sub">
                  {[r.target.district, r.target.joinedAt && `${user ? "joined" : "created"} ${fullDate(r.target.joinedAt)}`].filter(Boolean).join(", ")}
                </span>
              </div>
            </div>
            <Kv
              pairs={[
                ["Open reports", num(r.target.openReports)],
                ["Past actions", r.target.pastActions ? num(r.target.pastActions) : "None"],
                ...(user ? [] : ([["Organizer", r.target.organizerName ?? "—"]] as [string, string][])),
                ...(r.target.suspended ? ([["Status", <Status key="s" tone="danger">Suspended</Status>]] as [string, React.ReactNode][]) : []),
              ]}
            />
            {r.target.id && !r.target.deleted && (
              <Link className="go" href={user ? `/users/view/${qs({ id: r.target.id })}` : `/leagues/view/${qs({ id: r.target.id })}`}>
                Open {user ? "user" : "league"} page
                <Icon name="chevron_right" />
              </Link>
            )}
          </Panel>
          <Panel title="Reporter" sub="Only admins see this. Never shown to anyone else." right={<Status tone="neutral">Private</Status>}>
            {r.reporter ? (
              <PersonCell name={r.reporter.name ?? "No name yet"} sub={`${r.reporter.district ? `${r.reporter.district}, ` : ""}${r.reporter.reportsFiled} report${r.reporter.reportsFiled === 1 ? "" : "s"} filed in total`} />
            ) : (
              <span className="sub">The reporter&apos;s account was deleted.</span>
            )}
          </Panel>
          <Panel title="History">
            <div className="tl">
              <div className="tli">
                <span className="sub num">{stamp(r.createdAt)}</span>
                <i className="dot w" />
                <span>Report received</span>
              </div>
              {r.history.map((h) => (
                <div className="tli" key={h.id}>
                  <span className="sub num">{stamp(h.at)}</span>
                  <i className="dot" />
                  <span>
                    {actionLabel(h.action)} by {h.adminEmail?.split("@")[0] ?? "script"}
                    {h.note && <span className="sub"> · {h.note}</span>}
                  </span>
                </div>
              ))}
            </div>
          </Panel>
        </div>
      </div>

      {pending === "suspend" && (
        <Dialog
          title={`Suspend ${suspendName ?? "this account"}?`}
          width={520}
          onClose={() => setPending(null)}
          actions={
            <>
              <Btn onClick={() => setPending(null)}>Cancel</Btn>
              <Btn kind="danger" disabled={!noteOk || busy} onClick={() => suspend(false)}>
                Suspend account
              </Btn>
            </>
          }
        >
          <ul className="list">
            <li><Icon name="logout" /><span>They&apos;re signed out within 30 seconds, on every device.</span></li>
            <li><Icon name="block" /><span>Their number can&apos;t sign in or sign up again, even after the account is deleted.</span></li>
            <li><Icon name="visibility" /><span>Their rows, bids and results stay as they are for everyone else, with no label.</span></li>
            <li><Icon name="lock" /><span>They&apos;re never told who reported them.</span></li>
          </ul>
          <Field label="Decision note" help="Required. Saved to the audit log.">
            <NoteBox value={note} onChange={setNote} />
          </Field>
        </Dialog>
      )}
      {pending === "guard" && (
        <Dialog
          title={`Suspend ${suspendName ?? "this organizer"}?`}
          width={520}
          onClose={() => setPending(null)}
          actions={
            <>
              <Btn onClick={() => setPending(null)}>Cancel</Btn>
              <Btn kind="danger" disabled={!noteOk || !urgent || busy} onClick={() => suspend(true)}>
                Suspend account
              </Btn>
            </>
          }
        >
          <Banner tone="warn" icon="error">
            <b>They run a live auction.</b> Suspending now stops it for everyone until a co-organizer takes over. Wait until it ends unless this is an urgent safety case.
          </Banner>
          <div className="row" style={{ gap: 10 }}>
            <button type="button" className={`check${urgent ? " on" : ""}`} role="checkbox" aria-checked={urgent} onClick={() => setUrgent((v) => !v)} aria-label="Urgent safety case">
              {urgent && <Icon name="check" />}
            </button>
            <span style={{ font: "400 14px/1.4 var(--font-ui)" }}>This is an urgent safety case. Suspend during the auction.</span>
          </div>
          <Field label="Decision note">
            <NoteBox value={note} onChange={setNote} />
          </Field>
        </Dialog>
      )}
      {pending && typeof pending === "object" && (
        <Dialog
          title={`Remove this ${IMAGE_LABEL[pending.remove]}?`}
          width={540}
          onClose={() => setPending(null)}
          actions={
            <>
              <Btn onClick={() => setPending(null)}>Cancel</Btn>
              <Btn kind="danger" disabled={!noteOk || busy} onClick={() => remove(pending.remove)}>
                Remove {IMAGE_LABEL[pending.remove].split(" ").pop()}
              </Btn>
            </>
          }
        >
          <div style={{ display: "grid", gridTemplateColumns: "96px minmax(0, 1fr)", gap: 16 }}>
            <div className="imgph" style={{ height: 96 }}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              {evidenceImage ? <img src={evidenceImage} alt="" /> : "image"}
            </div>
            <ul className="list">
              <li><Icon name="hide_image" /><span>It disappears for everyone and is replaced with a neutral placeholder.</span></li>
              <li><Icon name="mail" /><span>The owner sees a one-time notice and can upload a new one.</span></li>
              <li><Icon name="delete" /><span>The stored file is deleted when nothing else uses it. The evidence link then stops working, so save a copy first if this may go further.</span></li>
            </ul>
          </div>
          {evidenceImage && (
            <a className="btn sm" href={evidenceImage} target="_blank" rel="noopener noreferrer" download>
              <Icon name="download" />
              Save a copy of the evidence
            </a>
          )}
          <Field label="Decision note">
            <NoteBox value={note} onChange={setNote} />
          </Field>
        </Dialog>
      )}
      {toast && (
        <Toast action="View in audit log" onAction={() => router.push("/audit/")} onDone={() => setToast(null)}>
          {toast}
        </Toast>
      )}
    </>
  );
}

export default function ReportViewPage() {
  return (
    <Suspense>
      <ReportView />
    </Suspense>
  );
}
