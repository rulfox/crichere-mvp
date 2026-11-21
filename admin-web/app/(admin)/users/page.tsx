"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { PageHead } from "@/components/Shell";
import { Btn, Chip, Dialog, Empty, Failed, Field, Icon, Pager, PersonCell, SelectChip, Status, Table, Words } from "@/components/ui";
import { ApiError, post, qs } from "@/lib/api";
import { day, num, roleLabel, shortDate, todayIst, USER_STATUS } from "@/lib/format";
import { useApi, useCursorPages } from "@/lib/hooks";
import type { Page, Today, UserRow } from "@/lib/types";

const lastActive = (d: string | null) => (d == null ? "—" : d === todayIst() ? "Today" : day(d));

/** Board U1/U2: read-only list; phone numbers are never shown, only matched exactly. */
export default function UsersPage() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [district, setDistrict] = useState("");
  const [reports, setReports] = useState(false);
  const [internal, setInternal] = useState(false);
  const [sort, setSort] = useState("newest");
  const [lookup, setLookup] = useState(false);
  useEffect(() => {
    const t = window.setTimeout(() => setQuery(q.trim()), 300);
    return () => window.clearTimeout(t);
  }, [q]);

  const filters = { q: query, status, district, hasOpenReports: reports, includeInternal: internal, sort };
  const pages = useCursorPages<Page<UserRow>["total"]>(JSON.stringify(filters));
  const list = useApi<Page<UserRow>>(`/api/v1/admin/users${qs({ ...filters, cursor: pages.cursor, limit: 25 })}`);
  const today = useApi<Today>("/api/v1/admin/insights/today");
  const total = pages.page === 0 ? (list.data?.total ?? null) : pages.total;
  const start = pages.page * 25;
  const anyFilter = query || status || district || reports;

  return (
    <>
      <PageHead
        title="Users"
        actions={
          <Btn icon="dialpad" onClick={() => setLookup(true)}>
            Find by phone number
          </Btn>
        }
      />
      {total && today.data && (
        <Words
          parts={[
            { t: `${total.capped ? "1000+" : num(total.count)} ${total.count === 1 ? "person" : "people"}`, b: true },
            { t: anyFilter ? " match these filters. " : ", " },
            ...(anyFilter ? [] : [{ t: num(today.data.live.today.activeUsers), b: true }, { t: " active today. " }]),
            { t: internal ? "Internal accounts are shown and marked." : "Internal accounts are hidden from counts and charts." },
          ]}
        />
      )}
      <div className="filters">
        <div className="input" style={{ width: 240 }}>
          <Icon name="search" />
          <input value={q} placeholder="Search name" onChange={(e) => setQ(e.target.value)} aria-label="Search name" />
        </div>
        <SelectChip label="Status" value={status} onChange={setStatus} options={[["", "Status: All"], ...Object.entries(USER_STATUS).filter(([k]) => k !== "deleted").map(([k, [, t]]) => [k, t] as [string, string])]} />
        <div className="input" style={{ width: 180 }}>
          <Icon name="location_on" />
          <input value={district} placeholder="District" onChange={(e) => setDistrict(e.target.value)} aria-label="District" />
        </div>
        <Chip icon="flag" on={reports} onClick={() => setReports((v) => !v)}>
          Has open reports
        </Chip>
        <Chip icon="check" on={internal} onClick={() => setInternal((v) => !v)}>
          Show internal
        </Chip>
        <SelectChip
          label="Sort"
          value={sort}
          onChange={setSort}
          options={[
            ["newest", "Newest first"],
            ["last_active", "Last active"],
            ["name", "Name"],
          ]}
        />
      </div>
      <div className="tablewrap">
        {list.error && !list.data ? (
          <Failed error={list.error} onRetry={list.reload} what="users" />
        ) : list.data && list.data.items.length === 0 ? (
          <Empty icon="filter_alt_off" title={anyFilter ? "Nobody matches these filters" : "No users yet"} />
        ) : (
          <div className={list.loading ? "fade" : undefined}>
            <Table<UserRow>
              rows={list.data?.items ?? []}
              rowKey={(u) => u.id}
              onRow={(u) => router.push(`/users/view/${qs({ id: u.id })}`)}
              columns={[
                { title: "Person", render: (u) => <PersonCell name={u.name} photo={u.photoUrl} sub={roleLabel(u.playingRole)} /> },
                { title: "District", render: (u) => u.district ?? "—" },
                { title: "Joined", render: (u) => <span className="num">{shortDate(u.joinedAt)}</span> },
                { title: "Last active", render: (u) => <span className="num">{lastActive(u.lastActive)}</span> },
                { title: "Organizes", right: true, render: (u) => <span className="num">{u.organizes}</span> },
                { title: "Owns", right: true, render: (u) => <span className="num">{u.owns}</span> },
                { title: "Plays", right: true, render: (u) => <span className="num">{u.plays}</span> },
                {
                  title: "Status",
                  render: (u) => (
                    <span className="row" style={{ gap: 6 }}>
                      <Status tone={USER_STATUS[u.status][0]}>{USER_STATUS[u.status][1]}</Status>
                      {u.internal && <Status tone="internal">Internal</Status>}
                    </span>
                  ),
                },
                { title: "Reports", right: true, render: (u) => (u.openReports ? <span className="num warn">{u.openReports}</span> : <span className="num sub">0</span>) },
              ]}
            />
          </div>
        )}
        {list.data && list.data.items.length > 0 && (
          <Pager
            left={`${num(start + 1)}–${num(start + list.data.items.length)}${total ? ` of ${total.capped ? "1000+" : num(total.count)}` : ""}`}
            onPrev={pages.prev}
            onNext={list.data.nextCursor ? () => pages.next(list.data!.nextCursor!, total) : undefined}
          />
        )}
      </div>
      {lookup && <PhoneLookup onClose={() => setLookup(false)} onOpen={(id) => router.push(`/users/view/${qs({ id })}`)} />}
    </>
  );
}

/** Board U2: the number lives only in this field while the dialog is open; never in a URL or history. */
function PhoneLookup({ onClose, onOpen }: { onClose: () => void; onOpen: (id: string) => void }) {
  const [phone, setPhone] = useState("");
  const [matches, setMatches] = useState<UserRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const find = async () => {
    setBusy(true);
    setError(null);
    try {
      setMatches(await post<UserRow[]>("/api/v1/admin/users/lookup", { phone }));
    } catch (e) {
      setError(e instanceof ApiError ? e.detail : "Lookup failed.");
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      title="Find by phone number"
      width={500}
      onClose={onClose}
      actions={
        <>
          <Btn onClick={onClose}>Close</Btn>
          <Btn kind="primary" disabled={busy || phone.replace(/\D/g, "").length < 10} onClick={find}>
            Find
          </Btn>
        </>
      }
    >
      <p>Type the full number. It&apos;s matched against the stored fingerprint, never shown or saved, and each lookup is recorded in the audit log.</p>
      <Field label="Phone number">
        <form
          className="input"
          onSubmit={(e) => {
            e.preventDefault();
            find();
          }}
        >
          <Icon name="call" />
          <input value={phone} inputMode="tel" autoComplete="off" placeholder="+91 98470 12345" onChange={(e) => setPhone(e.target.value)} aria-label="Phone number" />
        </form>
      </Field>
      {error && <span className="sub danger">{error}</span>}
      {matches && matches.length === 0 && <span className="sub">No account matches. Only an exact, full number can match; partial numbers find nothing.</span>}
      {matches?.map((m) => (
        <div key={m.id} className="pcard" style={{ display: "flex", alignItems: "center", justifyContent: "space-between", border: "1px solid var(--hair)", borderRadius: 12, padding: 14 }}>
          <PersonCell name={m.name} photo={m.photoUrl} sub={`${roleLabel(m.playingRole)}, ${m.district ?? "no district"}, joined ${shortDate(m.joinedAt)}`} />
          <Btn small icon="chevron_right" onClick={() => onOpen(m.id)}>
            Open
          </Btn>
        </div>
      ))}
    </Dialog>
  );
}
