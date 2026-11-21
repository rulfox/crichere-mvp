import { useEffect, useRef, type ReactNode } from "react";
import type { ApiError } from "@/lib/api";
import { initials, type Tone } from "@/lib/format";
import type { Sentence } from "@/lib/overview";

/* Components from docs/design/admin/admin-kit.js, as real controls. */

export const Icon = ({ name, className = "" }: { name: string; className?: string }) => (
  <span className={`ic ${className}`} aria-hidden="true">
    {name}
  </span>
);

const STATUS_ICON: Record<Tone, string> = {
  ok: "check_circle",
  warn: "error",
  danger: "cancel",
  info: "info",
  neutral: "radio_button_unchecked",
  live: "sensors",
  internal: "shield_person",
};

export const Status = ({ tone, children }: { tone: Tone; children: ReactNode }) => (
  <span className={`st ${tone}`}>
    <Icon name={STATUS_ICON[tone]} />
    {children}
  </span>
);

type BtnProps = {
  children?: ReactNode;
  icon?: string;
  kind?: "" | "primary" | "danger" | "dangero" | "ghost";
  small?: boolean;
  full?: boolean;
  disabled?: boolean;
  onClick?: () => void;
  type?: "button" | "submit";
  title?: string;
};

export function Btn({ children, icon, kind = "", small, full, disabled, onClick, type = "button", title }: BtnProps) {
  return (
    <button type={type} className={["btn", kind, small ? "sm" : "", full ? "full" : ""].join(" ").trim()} disabled={disabled} onClick={onClick} title={title}>
      {icon && <Icon name={icon} />}
      {children}
    </button>
  );
}

export const Chip = ({ on, icon, children, onClick }: { on?: boolean; icon?: string; children: ReactNode; onClick?: () => void }) => (
  <button type="button" className={`chip${on ? " on" : ""}`} onClick={onClick} aria-pressed={on}>
    {icon && <Icon name={icon} />}
    {children}
  </button>
);

/** A filter as a native select, styled as a chip so it matches the board. */
export function SelectChip<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: [T, string][];
  onChange: (v: T) => void;
}) {
  return (
    <select className={`chip${value ? " on" : ""}`} aria-label={label} value={value} onChange={(e) => onChange(e.target.value as T)}>
      {options.map(([v, t]) => (
        <option key={v} value={v}>
          {t}
        </option>
      ))}
    </select>
  );
}

export function Seg<T extends string>({ items, value, onChange, label }: { items: [T, string][]; value: T; onChange: (v: T) => void; label: string }) {
  return (
    <div className="segctl" role="radiogroup" aria-label={label}>
      {items.map(([v, t]) => (
        <button key={v} type="button" role="radio" aria-checked={v === value} className={v === value ? "on" : ""} onClick={() => onChange(v)}>
          {t}
        </button>
      ))}
    </div>
  );
}

export const Say = ({ children }: { children: ReactNode }) => <p className="say">{children}</p>;

/** A generated summary sentence; only figures are bold (board O1). */
export const Words = ({ parts }: { parts: Sentence }) => (
  <Say>
    {parts.map((p, i) => (p.b ? <b key={i}>{p.t}</b> : <span key={i}>{p.t}</span>))}
  </Say>
);

export function Panel({
  title,
  sub,
  right,
  className = "",
  children,
  loading,
}: {
  title?: ReactNode;
  sub?: ReactNode;
  right?: ReactNode;
  className?: string;
  children: ReactNode;
  loading?: boolean;
}) {
  return (
    <section className={`panel ${className}`}>
      {loading && (
        <div className="progress" aria-hidden="true">
          <i />
        </div>
      )}
      {(title || right) && (
        <header className="ph">
          <div className="col">
            {title && <h3>{title}</h3>}
            {sub && <span className="sub">{sub}</span>}
          </div>
          {right}
        </header>
      )}
      <div className={loading ? "fade" : undefined} style={{ display: "contents" }}>
        {children}
      </div>
    </section>
  );
}

export type Column<T> = { title: string; right?: boolean; width?: number; render: (row: T) => ReactNode };

export function Table<T>({ columns, rows, rowKey, onRow, selected }: { columns: Column<T>[]; rows: T[]; rowKey: (row: T) => string; onRow?: (row: T) => void; selected?: string }) {
  return (
    <table className="tbl">
      <thead>
        <tr>
          {columns.map((c, i) => (
            <th key={i} className={c.right ? "r" : undefined} style={c.width ? { width: c.width } : undefined}>
              {c.title}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => {
          const key = rowKey(row);
          return (
            <tr
              key={key}
              className={[onRow ? "link" : "", selected === key ? "sel" : ""].join(" ").trim() || undefined}
              onClick={onRow ? () => onRow(row) : undefined}
              tabIndex={onRow ? 0 : undefined}
              onKeyDown={onRow ? (e) => e.key === "Enter" && onRow(row) : undefined}
            >
              {columns.map((c, i) => (
                <td key={i} className={c.right ? "r" : undefined}>
                  {c.render(row)}
                </td>
              ))}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

export const Pager = ({ left, onPrev, onNext }: { left: ReactNode; onPrev?: () => void; onNext?: () => void }) => (
  <div className="pager">
    <span>{left}</span>
    <div style={{ display: "flex", gap: 8 }}>
      <Btn small icon="chevron_left" disabled={!onPrev} onClick={onPrev}>
        Previous
      </Btn>
      <Btn small icon="chevron_right" disabled={!onNext} onClick={onNext}>
        Next
      </Btn>
    </div>
  </div>
);

export function Dialog({ title, children, actions, width = 480, onClose }: { title: string; children: ReactNode; actions: ReactNode; width?: number; onClose?: () => void }) {
  const box = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const first = box.current?.querySelector<HTMLElement>("textarea, input, button");
    first?.focus();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose?.();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <>
      <div className="scrim" onClick={onClose} />
      <div className="dlg" role="dialog" aria-modal="true" aria-label={title} style={{ width }} ref={box}>
        <h3>{title}</h3>
        {children}
        <div className="acts">{actions}</div>
      </div>
    </>
  );
}

export const Banner = ({ tone, icon, children }: { tone: "warn" | "info" | "danger" | "neutral"; icon: string; children: ReactNode }) => (
  <div className={`banner ${tone}`} role={tone === "danger" ? "alert" : undefined}>
    <Icon name={icon} className={tone} />
    <div>{children}</div>
  </div>
);

export const Kv = ({ pairs }: { pairs: [ReactNode, ReactNode][] }) => (
  <div className="kvg">
    {pairs.map(([k, v], i) => (
      <KvPair key={i} k={k} v={v} />
    ))}
  </div>
);

const KvPair = ({ k, v }: { k: ReactNode; v: ReactNode }) => (
  <>
    <span>{k}</span>
    <span>{v ?? "—"}</span>
  </>
);

export const Empty = ({ icon, title, children, action }: { icon: string; title: string; children?: ReactNode; action?: ReactNode }) => (
  <div className="empty">
    <Icon name={icon} />
    <h3>{title}</h3>
    {children && <p>{children}</p>}
    {action}
  </div>
);

/** Board X5 (one part fails) and X7 (backend unreachable). */
export function Failed({ error, onRetry, what = "this" }: { error: ApiError; onRetry: () => void; what?: string }) {
  if (error.unreachable) {
    return (
      <Empty icon="cloud_off" title="Can't reach Crichere right now" action={<Btn small icon="refresh" onClick={onRetry}>Try again</Btn>}>
        The admin site is up, but the backend didn&apos;t answer. Nothing was changed. Error <span className="num">ADM-503</span>.
      </Empty>
    );
  }
  return (
    <Empty icon="error" title={`Couldn't load ${what}`} action={<Btn small icon="refresh" onClick={onRetry}>Try again</Btn>}>
      The rest of the page is fine. Error <span className="num">{error.code}</span>
      {error.detail ? `: ${error.detail}` : "."}
    </Empty>
  );
}

export const Avatar = ({ name, photo, big, neutral }: { name: string; photo?: string | null; big?: boolean; neutral?: boolean }) => (
  <span className={`av${big ? " big" : ""}${neutral ? " neutral" : ""}`}>
    {/* eslint-disable-next-line @next/next/no-img-element */}
    {neutral ? <Icon name="person" /> : photo ? <img src={photo} alt="" /> : initials(name)}
  </span>
);

const SQUARES = ["#0B6E4F", "#2A78D6", "#C2410C", "#2E9070", "#084C38", "#5C645F"];

/** League initials on a colour picked from the name, as on the boards. */
export function LeagueSquare({ name, logo, big }: { name: string; logo?: string | null; big?: boolean }) {
  const colour = SQUARES[[...name].reduce((s, c) => s + c.charCodeAt(0), 0) % SQUARES.length];
  return (
    <span className={`lsq${big ? " big" : ""}`} style={{ background: colour }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      {logo ? <img src={logo} alt="" /> : initials(name)}
    </span>
  );
}

export const PersonCell = ({ name, sub, photo, neutral }: { name: string; sub?: ReactNode; photo?: string | null; neutral?: boolean }) => (
  <div className="cell">
    <Avatar name={name} photo={photo} neutral={neutral || name === "Deleted user"} />
    <div className="col">
      <span className="strong">{name}</span>
      {sub && <span className="sub">{sub}</span>}
    </div>
  </div>
);

export const LeagueCell = ({ name, sub, logo }: { name: string; sub?: ReactNode; logo?: string | null }) => (
  <div className="cell">
    <LeagueSquare name={name} logo={logo} />
    <div className="col">
      <span className="strong">{name}</span>
      {sub && <span className="sub">{sub}</span>}
    </div>
  </div>
);

export function Tabs<T extends string>({ items, value, onChange }: { items: [T, string, number?][]; value: T; onChange: (t: T) => void }) {
  return (
    <div className="tabbar" role="tablist">
      {items.map(([v, t, n]) => (
        <button key={v} type="button" role="tab" aria-selected={v === value} className={v === value ? "on" : ""} onClick={() => onChange(v)}>
          {t}
          {n != null && <b className="cnt">{n}</b>}
        </button>
      ))}
    </div>
  );
}

export function Field({ label, help, children }: { label: string; help?: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {help && <span className="sub">{help}</span>}
    </div>
  );
}

export const NoteBox = ({ value, onChange, placeholder = "Required" }: { value: string; onChange: (v: string) => void; placeholder?: string }) => (
  <div className="input area">
    <textarea aria-label="Decision note" value={value} placeholder={placeholder} maxLength={1000} onChange={(e) => onChange(e.target.value)} />
  </div>
);

export function Toast({ children, action, onAction, onDone }: { children: ReactNode; action?: string; onAction?: () => void; onDone: () => void }) {
  useEffect(() => {
    const t = window.setTimeout(onDone, 6000);
    return () => window.clearTimeout(t);
  }, [onDone]);
  return (
    <div className="toast" role="status">
      {children}
      {action && (
        <button type="button" onClick={onAction}>
          {action}
        </button>
      )}
    </div>
  );
}

export const Loading = () => (
  <div className="progress" aria-label="Loading">
    <i />
  </div>
);
