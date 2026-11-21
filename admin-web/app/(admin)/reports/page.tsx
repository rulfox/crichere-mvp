"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { PageHead } from "@/components/Shell";
import { Empty, Failed, Icon, LeagueCell, Pager, PersonCell, SelectChip, Seg, Status, Table, Words } from "@/components/ui";
import { qs } from "@/lib/api";
import { age, ageHours, num, REASONS, reasonLabel, reasonTone } from "@/lib/format";
import { useApi, useCursorPages } from "@/lib/hooks";
import type { Page, ReportListItem, Today } from "@/lib/types";

type View = "OPEN" | "RESOLVED" | "DISMISSED" | "ALL";

/** Board M1: oldest first for open reports, because the 24-hour target is about waiting time. */
export default function ReportsPage() {
  const router = useRouter();
  const [view, setView] = useState<View>("OPEN");
  const [reason, setReason] = useState("");
  const filterKey = `${view}|${reason}`;
  const pages = useCursorPages(filterKey);
  const list = useApi<Page<ReportListItem>>(`/api/v1/admin/reports${qs({ status: view, reason, cursor: pages.cursor, limit: 25 })}`);
  const today = useApi<Today>("/api/v1/admin/insights/today");
  const open = today.data?.moderation.openReports;
  const oldest = today.data?.moderation.oldestOpenAt;

  return (
    <>
      <PageHead title="Reports" />
      {today.data && (
        <Words
          parts={
            open
              ? [
                  { t: `${num(open)} open`, b: true },
                  { t: ". The oldest has waited " },
                  { t: oldest ? age(oldest) : "—", b: true },
                  { t: "; the target is 24 hours. Handle Under 18 and Hate or harassment first when they appear." },
                ]
              : [{ t: "Nothing is waiting." }]
          }
        />
      )}
      <div className="filters">
        <Seg<View>
          label="Status"
          items={[
            ["OPEN", open != null ? `Open ${open}` : "Open"],
            ["RESOLVED", "Resolved"],
            ["DISMISSED", "Dismissed"],
            ["ALL", "All"],
          ]}
          value={view}
          onChange={setView}
        />
        <SelectChip label="Reason" value={reason} onChange={setReason} options={[["", "Reason: All"], ...Object.entries(REASONS).map(([k, v]) => [k, v] as [string, string])]} />
        <span className="chip on">
          <Icon name="swap_vert" />
          {view === "OPEN" ? "Oldest first" : "Newest first"}
        </span>
      </div>
      <div className="tablewrap" style={{ position: "relative" }}>
        {list.error && !list.data ? (
          <Failed error={list.error} onRetry={list.reload} what="reports" />
        ) : list.data && list.data.items.length === 0 ? (
          view === "OPEN" && !reason ? (
            <Empty icon="task_alt" title="No open reports">New reports show up here, and an alert email goes out within 15 minutes.</Empty>
          ) : (
            <Empty icon="filter_alt_off" title="No reports match these filters" />
          )
        ) : (
          <div className={list.loading ? "fade" : undefined}>
            <Table<ReportListItem>
              rows={list.data?.items ?? []}
              rowKey={(r) => r.id}
              onRow={(r) => router.push(`/reports/view/${qs({ id: r.id })}`)}
              columns={[
                { title: "Reason", width: 190, render: (r) => <Status tone={reasonTone(r.reason)}>{reasonLabel(r.reason)}</Status> },
                {
                  title: "About",
                  render: (r) =>
                    r.targetType === "USER" ? (
                      <PersonCell name={r.targetName ?? "Deleted user"} sub="Person" />
                    ) : (
                      <LeagueCell name={r.targetName ?? "Deleted league"} sub="League" />
                    ),
                },
                { title: "Reporter's note", render: (r) => (r.detailsPreview ? `“${r.detailsPreview}”` : <span className="sub">No note</span>) },
                {
                  title: view === "OPEN" ? "Waiting" : "Received",
                  width: 110,
                  render: (r) => (
                    <span className={`num${view === "OPEN" && ageHours(r.createdAt) >= 18 ? " warn" : ""}`} style={{ fontSize: 13 }}>
                      {view === "OPEN" && ageHours(r.createdAt) >= 18 && <Icon name="schedule" className="warn" />} {age(r.createdAt)}
                    </span>
                  ),
                },
                { title: "Reports", right: true, width: 80, render: (r) => <span className="num">{r.openOnTarget}</span> },
                { title: "", width: 30, render: () => <Icon name="chevron_right" className="go" /> },
              ]}
            />
          </div>
        )}
        {list.data && list.data.items.length > 0 && (
          <Pager
            left={`${list.data.items.length} ${view === "OPEN" ? "open " : ""}report${list.data.items.length === 1 ? "" : "s"} on this page`}
            onPrev={pages.prev}
            onNext={list.data.nextCursor ? () => pages.next(list.data!.nextCursor!) : undefined}
          />
        )}
      </div>
    </>
  );
}
