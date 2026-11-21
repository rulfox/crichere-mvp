"use client";

import Link from "next/link";
import { useState } from "react";
import { PageHead } from "@/components/Shell";
import { Banner, Btn, Dialog, Empty, Failed, Field, NoteBox, PersonCell, Table, Tabs, Toast, Words } from "@/components/ui";
import { ApiError, post, qs } from "@/lib/api";
import { fullDate, num } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { SuspendedOverview } from "@/lib/types";

type Account = SuspendedOverview["accounts"][number];
type Number_ = SuspendedOverview["blockedNumbers"][number];
type Pending = { kind: "unsuspend"; account: Account } | { kind: "release"; number: Number_ } | null;

/** Board M6: suspended accounts and numbers blocked after their account was deleted. */
export default function SuspendedPage() {
  const data = useApi<SuspendedOverview>("/api/v1/admin/suspended");
  const [tab, setTab] = useState<"accounts" | "numbers">("accounts");
  const [pending, setPending] = useState<Pending>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const run = async () => {
    if (!pending) return;
    setBusy(true);
    setProblem(null);
    try {
      if (pending.kind === "unsuspend") await post(`/api/v1/admin/users/${pending.account.userId}/unsuspend`, { note: note.trim() });
      else await post(`/api/v1/admin/suspended-numbers/${encodeURIComponent(pending.number.hash)}/release`, { note: note.trim() });
      setToast(pending.kind === "unsuspend" ? `${pending.account.name ?? "Account"} can sign in again.` : "Number released.");
      setPending(null);
      setNote("");
      data.reload();
    } catch (e) {
      setProblem(e instanceof ApiError ? e.detail : "Something went wrong. Nothing was changed.");
    } finally {
      setBusy(false);
    }
  };

  const d = data.data;
  return (
    <>
      <PageHead title="Suspended" />
      {d && (
        <Words
          parts={[
            { t: `${num(d.accounts.length)} account${d.accounts.length === 1 ? "" : "s"}`, b: true },
            { t: d.accounts.length === 1 ? " is suspended. " : " are suspended. " },
            { t: `${num(d.blockedNumbers.length)} number${d.blockedNumbers.length === 1 ? "" : "s"}`, b: true },
            { t: d.blockedNumbers.length === 1 ? " stays blocked after its account was deleted." : " stay blocked after their accounts were deleted." },
          ]}
        />
      )}
      <Tabs
        items={[
          ["accounts", "Accounts", d?.accounts.length],
          ["numbers", "Blocked numbers", d?.blockedNumbers.length],
        ]}
        value={tab}
        onChange={setTab}
      />
      <div className="tablewrap">
        {data.error && !d ? (
          <Failed error={data.error} onRetry={data.reload} what="suspended accounts" />
        ) : !d ? null : tab === "accounts" ? (
          d.accounts.length === 0 ? (
            <Empty icon="task_alt" title="No suspended accounts" />
          ) : (
            <Table<Account>
              rows={d.accounts}
              rowKey={(a) => a.userId}
              columns={[
                { title: "Person", render: (a) => <Link href={`/users/view/${qs({ id: a.userId })}`}><PersonCell name={a.name ?? "No name yet"} sub={a.district ?? undefined} /></Link> },
                { title: "Suspended", render: (a) => <span className="num">{fullDate(a.suspendedAt)}</span> },
                { title: "By", render: (a) => (a.by ?? "script").split("@")[0] },
                { title: "Note", render: (a) => <span className="sub" style={{ fontSize: 13 }}>{a.note ?? "—"}</span> },
                { title: "Report", render: (a) => (a.reportId ? <Link href={`/reports/view/${qs({ id: a.reportId })}`}>{a.reportId.slice(0, 8)}</Link> : "—") },
                { title: "", width: 120, render: (a) => <Btn small onClick={() => setPending({ kind: "unsuspend", account: a })}>Unsuspend</Btn> },
              ]}
            />
          )
        ) : d.blockedNumbers.length === 0 ? (
          <Empty icon="task_alt" title="No blocked numbers" />
        ) : (
          <Table<Number_>
            rows={d.blockedNumbers}
            rowKey={(n) => n.hash}
            columns={[
              { title: "Number", render: (n) => <span className="sub">Fingerprint {n.hash.slice(0, 10)}…, the number itself is never stored</span> },
              { title: "Blocked since", render: (n) => <span className="num">{fullDate(n.since)}</span> },
              { title: "", width: 120, render: (n) => <Btn small onClick={() => setPending({ kind: "release", number: n })}>Release</Btn> },
            ]}
          />
        )}
      </div>
      <Banner tone="neutral" icon="info">
        Unsuspending lets them sign in again straight away. A blocked number belongs to an account that was deleted; releasing it lets that number create a new account.
      </Banner>
      {pending && (
        <Dialog
          title={pending.kind === "unsuspend" ? `Unsuspend ${pending.account.name ?? "this account"}?` : "Release this number?"}
          width={480}
          onClose={() => setPending(null)}
          actions={
            <>
              <Btn onClick={() => setPending(null)}>Cancel</Btn>
              <Btn kind="primary" disabled={!note.trim() || busy} onClick={run}>
                {pending.kind === "unsuspend" ? "Unsuspend" : "Release number"}
              </Btn>
            </>
          }
        >
          <p>{pending.kind === "unsuspend" ? "They can sign in again straight away." : "The number can sign up again as a new, empty account."}</p>
          {problem && <Banner tone="danger" icon="cancel">{problem}</Banner>}
          <Field label="Decision note" help="Required. Saved to the audit log.">
            <NoteBox value={note} onChange={setNote} />
          </Field>
        </Dialog>
      )}
      {toast && <Toast onDone={() => setToast(null)}>{toast}</Toast>}
    </>
  );
}
