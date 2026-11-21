"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { PageHead } from "@/components/Shell";
import { Btn, Chip, Empty, Failed, Icon, LeagueCell, Pager, SelectChip, Status, Table, Words } from "@/components/ui";
import { qs } from "@/lib/api";
import { day, LEAGUE_STATUS, num, shortDate } from "@/lib/format";
import { useApi, useCursorPages } from "@/lib/hooks";
import type { LeagueRow, Page } from "@/lib/types";

/** Board L1: read-only list, keyset paging, total counted once per filter change. */
export default function LeaguesPage() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [district, setDistrict] = useState("");
  const [reports, setReports] = useState(false);
  const [internal, setInternal] = useState(false);
  const [sort, setSort] = useState("newest");
  useEffect(() => {
    const t = window.setTimeout(() => setQuery(q.trim()), 300);
    return () => window.clearTimeout(t);
  }, [q]);

  const filters = { q: query, status, district, hasOpenReports: reports, includeInternal: internal, sort };
  const filterKey = JSON.stringify(filters);
  const pages = useCursorPages<Page<LeagueRow>["total"]>(filterKey);
  const list = useApi<Page<LeagueRow>>(`/api/v1/admin/leagues${qs({ ...filters, cursor: pages.cursor, limit: 25 })}`);
  const total = pages.page === 0 ? (list.data?.total ?? null) : pages.total;
  const anyFilter = query || status || district || reports;
  const clear = () => {
    setQ("");
    setStatus("");
    setDistrict("");
    setReports(false);
  };
  const totalText = total ? `${total.capped ? "1000+" : num(total.count)} league${total.count === 1 ? "" : "s"}` : "";
  const start = pages.page * 25;

  return (
    <>
      <PageHead title="Leagues" />
      {total && (
        <Words
          parts={[
            { t: totalText, b: true },
            { t: anyFilter ? " match these filters. " : ". " },
            { t: sort === "newest" ? "Newest first." : sort === "starts" ? "By start date." : "By name." },
          ]}
        />
      )}
      <div className="filters">
        <div className="input" style={{ width: 280 }}>
          <Icon name="search" />
          <input value={q} placeholder="Search league or organizer" onChange={(e) => setQ(e.target.value)} aria-label="Search league or organizer" />
        </div>
        <SelectChip label="Status" value={status} onChange={setStatus} options={[["", "Status: All"], ...Object.entries(LEAGUE_STATUS).map(([k, [, t]]) => [k, t] as [string, string])]} />
        <div className="input" style={{ width: 180 }}>
          <Icon name="location_on" />
          <input value={district} placeholder="District" onChange={(e) => setDistrict(e.target.value)} aria-label="District" />
        </div>
        <Chip icon="flag" on={reports} onClick={() => setReports((v) => !v)}>
          Has open reports
        </Chip>
        <Chip icon="check" on={!internal} onClick={() => setInternal((v) => !v)}>
          {internal ? "Internal shown" : "Internal hidden"}
        </Chip>
        <SelectChip
          label="Sort"
          value={sort}
          onChange={setSort}
          options={[
            ["newest", "Newest first"],
            ["starts", "Start date"],
            ["name", "Name"],
          ]}
        />
      </div>
      <div className="tablewrap">
        {list.error && !list.data ? (
          <Failed error={list.error} onRetry={list.reload} what="leagues" />
        ) : list.data && list.data.items.length === 0 ? (
          <Empty icon="filter_alt_off" title={anyFilter ? "No leagues match these filters" : "No leagues yet"} action={anyFilter ? <Btn onClick={clear}>Clear filters</Btn> : undefined}>
            {anyFilter ? "Remove a filter to see more." : undefined}
          </Empty>
        ) : (
          <div className={list.loading ? "fade" : undefined}>
            <Table<LeagueRow>
              rows={list.data?.items ?? []}
              rowKey={(l) => l.id}
              onRow={(l) => router.push(`/leagues/view/${qs({ id: l.id })}`)}
              columns={[
                { title: "League", render: (l) => <LeagueCell name={l.name} logo={l.logoUrl} sub={`Organizer ${l.organizerName}`} /> },
                { title: "District", render: (l) => l.district },
                { title: "Starts", render: (l) => <span className="num">{day(l.startsOn)}</span> },
                {
                  title: "Status",
                  render: (l) => (
                    <span className="row" style={{ gap: 6 }}>
                      <Status tone={LEAGUE_STATUS[l.status][0]}>{LEAGUE_STATUS[l.status][1]}</Status>
                      {l.internal && <Status tone="internal">Internal</Status>}
                    </span>
                  ),
                },
                { title: "Franchises", right: true, render: (l) => <span className="num">{l.franchises}/{l.franchisesRequired ?? "—"}</span> },
                { title: "Players", right: true, render: (l) => <span className="num">{l.players}/{l.playersRequired ?? "—"}</span> },
                {
                  title: "Open reports",
                  right: true,
                  render: (l) =>
                    l.openReports ? (
                      <span className="num warn">
                        <Icon name="flag" className="warn" /> {l.openReports}
                      </span>
                    ) : (
                      <span className="num sub">0</span>
                    ),
                },
                { title: "Created", render: (l) => <span className="num">{shortDate(l.createdAt)}</span> },
              ]}
            />
          </div>
        )}
        {list.data && list.data.items.length > 0 && (
          <Pager
            left={`${num(start + 1)}–${num(start + list.data.items.length)}${totalText ? ` of ${totalText.split(" ")[0]}` : ""}`}
            onPrev={pages.prev}
            onNext={list.data.nextCursor ? () => pages.next(list.data!.nextCursor!, total) : undefined}
          />
        )}
      </div>
    </>
  );
}
