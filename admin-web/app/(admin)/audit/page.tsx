"use client";

import Link from "next/link";
import { useState } from "react";
import { PageHead } from "@/components/Shell";
import { Empty, Failed, Pager, SelectChip, Status, Table, Words } from "@/components/ui";
import { qs } from "@/lib/api";
import { ACTIONS, actionLabel, actionTone, stamp } from "@/lib/format";
import { useApi, useCursorPages } from "@/lib/hooks";
import type { AuditEntry, Page } from "@/lib/types";

/** Board M7: every admin action, view of a payment proof and phone lookup, newest first. Read-only. */
export default function AuditPage() {
  const [action, setAction] = useState("");
  const [admin, setAdmin] = useState("");
  const pages = useCursorPages(`${action}|${admin}`);
  const list = useApi<Page<AuditEntry>>(`/api/v1/admin/audit${qs({ action, admin, cursor: pages.cursor, limit: 50 })}`);

  const about = (e: AuditEntry) => {
    if (e.reportId) return <Link href={`/reports/view/${qs({ id: e.reportId })}`}>Report {e.reportId.slice(0, 8)}</Link>;
    if (e.userId) return <Link href={`/users/view/${qs({ id: e.userId })}`}>Person</Link>;
    if (e.leagueId) return <Link href={`/leagues/view/${qs({ id: e.leagueId })}`}>League</Link>;
    return "";
  };

  return (
    <>
      <PageHead title="Audit log" />
      <Words parts={[{ t: "Every admin action, view of a payment proof and phone lookup, newest first. Nothing here can be edited or deleted." }]} />
      <div className="filters">
        <SelectChip label="Action" value={action} onChange={setAction} options={[["", "Action: All"], ...Object.entries(ACTIONS).map(([k, v]) => [k, v] as [string, string])]} />
        <SelectChip
          label="Admin"
          value={admin}
          onChange={setAdmin}
          options={[
            ["", "Admin: All"],
            ["script", "Script"],
          ]}
        />
      </div>
      <div className="tablewrap">
        {list.error && !list.data ? (
          <Failed error={list.error} onRetry={list.reload} what="the audit log" />
        ) : list.data && list.data.items.length === 0 ? (
          <Empty icon="history" title="Nothing logged for these filters" />
        ) : (
          <div className={list.loading ? "fade" : undefined}>
            <Table<AuditEntry>
              rows={list.data?.items ?? []}
              rowKey={(e) => e.id}
              columns={[
                { title: "When", width: 130, render: (e) => <span className="num" style={{ fontSize: 12 }}>{stamp(e.at)}</span> },
                { title: "Admin", render: (e) => (e.adminEmail ?? "script").split("@")[0] },
                { title: "Action", render: (e) => <Status tone={actionTone(e.action)}>{actionLabel(e.action)}</Status> },
                { title: "About", render: about },
                { title: "Note", render: (e) => <span className="sub" style={{ fontSize: 13 }}>{e.note ?? ""}</span> },
              ]}
            />
          </div>
        )}
        {list.data && list.data.items.length > 0 && (
          <Pager left={`Showing ${list.data.items.length}${pages.page ? ` (page ${pages.page + 1})` : ""}`} onPrev={pages.prev} onNext={list.data.nextCursor ? () => pages.next(list.data!.nextCursor!) : undefined} />
        )}
      </div>
    </>
  );
}
