import { scaleLinear } from "@visx/scale";
import { AreaClosed, LinePath } from "@visx/shape";
import { useState, type ReactNode } from "react";
import { day as dayLabel, num } from "@/lib/format";
import { Icon, Panel } from "./ui";

/*
 * Chart forms from admin-kit.js (line, columns, paired, ranked, funnel, segbar, heat, meter, spark, cal),
 * following the dataviz rules: one axis, hover readout, every chart switchable to a table, and a
 * refetch keeps the old chart faded under a progress line (Panel `loading`).
 */

export const RAMP = ["#E7F3EE", "#C6E3D8", "#5FB294", "#2E9070", "#0B6E4F", "#084C38"];
export const SERIES = ["var(--c0)", "var(--c1)", "var(--c2)"];

export type TableData = { head: string[]; rows: (string | number)[][] };

/** A panel whose chart can switch to a table (the "Table" control on every board chart). */
export function ChartPanel({
  title,
  sub,
  className,
  table,
  loading,
  children,
}: {
  title: string;
  sub?: ReactNode;
  className?: string;
  table?: TableData;
  loading?: boolean;
  children: ReactNode;
}) {
  const [asTable, setAsTable] = useState(false);
  const right = table ? (
    <button type="button" className={`tv${asTable ? " on" : ""}`} onClick={() => setAsTable((v) => !v)} aria-pressed={asTable}>
      <Icon name={asTable ? "monitoring" : "table_rows"} />
      {asTable ? "Chart" : "Table"}
    </button>
  ) : (
    <span />
  );
  return (
    <Panel title={title} sub={sub} className={className} right={right} loading={loading}>
      {asTable && table ? <DataTable data={table} /> : children}
    </Panel>
  );
}

export const DataTable = ({ data }: { data: TableData }) => (
  <div style={{ maxHeight: 260, overflow: "auto" }}>
    <table className="tbl">
      <thead>
        <tr>
          {data.head.map((h, i) => (
            <th key={h} className={i ? "r" : undefined}>
              {h}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.rows.map((r, i) => (
          <tr key={i}>
            {r.map((v, j) => (
              <td key={j} className={j ? "r num" : undefined}>
                {typeof v === "number" ? num(v) : v}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

export const Legend = ({ items }: { items: [string, string, ("line" | "box")?][] }) => (
  <div className="legend">
    {items.map(([color, label, kind]) => (
      <span className="lg" key={label}>
        {kind === "box" ? <i style={{ background: color }} /> : <b style={{ background: color }} />}
        {label}
      </span>
    ))}
  </div>
);

type Series = { label: string; color: string; values: number[]; dashed?: boolean };

/** Line chart (one axis), area under a single series, hover readout for every series at that day. */
export function LineChart({ days, series, height = 170, width = 556, endLabels = false }: { days: string[]; series: Series[]; height?: number; width?: number; endLabels?: boolean }) {
  const [hover, setHover] = useState<number | null>(null);
  const all = series.flatMap((s) => s.values);
  // At least 2 so the middle tick is a whole number; small series never repeat a tick label.
  const max = Math.max(2, niceMax(Math.max(1, ...all)));
  const x = scaleLinear<number>({ domain: [0, Math.max(1, days.length - 1)], range: [0, width] });
  const y = scaleLinear<number>({ domain: [0, max], range: [height, 0] });
  const ticks = [0, max / 2, max];

  const onMove = (e: React.MouseEvent<SVGRectElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const i = Math.round(x.invert(((e.clientX - rect.left) / rect.width) * width));
    setHover(Math.min(days.length - 1, Math.max(0, i)));
  };

  return (
    <div className="chart" style={{ maxWidth: width + 44 }}>
      <svg width="100%" viewBox={`-36 -6 ${width + (endLabels ? 90 : 44)} ${height + 26}`} role="img" aria-label={series.map((s) => s.label).join(", ")}>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={0} x2={width} y1={y(t)} y2={y(t)} stroke="var(--grid)" />
            <text x={-8} y={y(t) + 4} textAnchor="end" fontFamily="var(--font-mono)" fontSize={11} fill="var(--ink2)">
              {num(Math.round(t))}
            </text>
          </g>
        ))}
        {series.length === 1 && (
          <AreaClosed data={series[0].values} x={(_, i) => x(i)} y={(v) => y(v)} yScale={y} fill={series[0].color} fillOpacity={0.12} />
        )}
        {series.map((s) => (
          <LinePath key={s.label} data={s.values} x={(_, i) => x(i)} y={(v) => y(v)} stroke={s.color} strokeWidth={2} strokeLinejoin="round" strokeDasharray={s.dashed ? "4 4" : undefined} />
        ))}
        {endLabels &&
          spreadLabels(series.map((s) => y(s.values[s.values.length - 1] ?? 0) + 4), 12).map((labelY, i) => (
            <text key={series[i].label} x={width + 6} y={labelY} fontSize={11} fontWeight={500} fill="var(--ink2)">
              {series[i].label}
            </text>
          ))}
        {hover != null && (
          <g>
            <line x1={x(hover)} x2={x(hover)} y1={0} y2={height} stroke="var(--hover)" />
            {series.map((s) => (
              <circle key={s.label} cx={x(hover)} cy={y(s.values[hover] ?? 0)} r={4} fill={s.color} stroke="var(--dotring)" strokeWidth={2} />
            ))}
          </g>
        )}
        <text x={0} y={height + 18} fontFamily="var(--font-mono)" fontSize={11} fill="var(--ink2)">
          {days.length ? dayLabel(days[0]) : ""}
        </text>
        <text x={width} y={height + 18} textAnchor="end" fontFamily="var(--font-mono)" fontSize={11} fill="var(--ink2)">
          {days.length ? dayLabel(days[days.length - 1]) : ""}
        </text>
        <rect x={0} y={0} width={width} height={height} fill="transparent" onMouseMove={onMove} onMouseLeave={() => setHover(null)} />
      </svg>
      {hover != null && (
        <div className="tip" style={{ left: `${Math.min(70, (x(hover) / width) * 100)}%`, top: 8 }}>
          <span className="sub">{dayLabel(days[hover])}</span>
          {series.map((s) => (
            <div className="tr" key={s.label}>
              <b style={{ background: s.color }} />
              {s.label}
              <strong>{num(s.values[hover] ?? 0)}</strong>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** Moves end labels apart so lines ending at the same value don't print on top of each other. */
export function spreadLabels(ys: number[], gap: number): number[] {
  const order = ys.map((y, i) => ({ y, i })).sort((a, b) => a.y - b.y);
  for (let k = 1; k < order.length; k++) {
    if (order[k].y - order[k - 1].y < gap) order[k].y = order[k - 1].y + gap;
  }
  const out = new Array<number>(ys.length);
  order.forEach((o) => (out[o.i] = o.y));
  return out;
}

function niceMax(v: number): number {
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  const n = v / p;
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
  return step * p;
}

export function Columns({ values, labels, height = 110, color = "var(--c0)", x }: { values: number[]; labels: string[]; height?: number; color?: string; x?: [string, string] }) {
  const max = Math.max(...values, 1);
  return (
    <>
      <div className="cols" style={{ height }}>
        {values.map((v, i) => (
          <div className="cc" key={i} title={`${labels[i]}: ${num(v)}`}>
            <span className="num cv">{v}</span>
            <div style={{ height: Math.max((v / max) * (height - 18), v ? 3 : 0), background: color }} />
          </div>
        ))}
      </div>
      {x && (
        <div className="xax sub">
          <span>{x[0]}</span>
          <span>{x[1]}</span>
        </div>
      )}
    </>
  );
}

export function Paired({ a, b, labels, height = 110, ca = "var(--c2)", cb = "var(--c0)", x }: { a: number[]; b: number[]; labels: string[]; height?: number; ca?: string; cb?: string; x?: [string, string] }) {
  const max = Math.max(...a, ...b, 1);
  return (
    <>
      <div className="cols" style={{ height, gap: 6 }}>
        {a.map((v, i) => (
          <div className="cp" key={i} title={`${labels[i]}: ${v} opened, ${b[i]} closed`}>
            <div style={{ height: (v / max) * height, background: ca }} />
            <div style={{ height: (b[i] / max) * height, background: cb }} />
          </div>
        ))}
      </div>
      {x && (
        <div className="xax sub">
          <span>{x[0]}</span>
          <span>{x[1]}</span>
        </div>
      )}
    </>
  );
}

export function Ranked({ rows, color = "#2E9070", max }: { rows: { key: string; value: number }[]; color?: string; max?: number }) {
  const m = max ?? Math.max(1, ...rows.map((r) => r.value));
  return (
    <div className="rank">
      {rows.map((r) => (
        <div className="rrow" key={r.key}>
          <span className="rk">{r.key}</span>
          <div className="rtrack">
            <div style={{ width: `${((r.value / m) * 100).toFixed(1)}%`, background: color }} />
          </div>
          <span className="num rv">{num(r.value)}</span>
        </div>
      ))}
    </div>
  );
}

const FUNNEL = ["#084C38", "#0B6E4F", "#2E9070", "#5FB294"];

export function Funnel({ rows }: { rows: { key: string; value: number }[] }) {
  const top = Math.max(1, rows[0]?.value ?? 1);
  return (
    <div className="rank">
      {rows.map((r, i) => (
        <div className="frow" key={r.key}>
          <div className="fhead">
            <span className="rk">{r.key}</span>
            <span className="num rv">{num(r.value)}</span>
          </div>
          <div className="rtrack tall">
            <div style={{ width: `${((r.value / top) * 100).toFixed(1)}%`, background: FUNNEL[i] }} />
          </div>
          <span className="sub">
            {i === 0 ? "Everyone who asked for a code" : rows[i - 1].value ? `${Math.round((r.value / rows[i - 1].value) * 100)}% of previous step` : "—"}
          </span>
        </div>
      ))}
    </div>
  );
}

export function SegBar({ rows, colors, inks }: { rows: { key: string; value: number }[]; colors: string[]; inks: string[] }) {
  const shown = rows.filter((r) => r.value > 0);
  return (
    <>
      <div className="segb">
        {shown.map((r) => {
          const i = rows.indexOf(r);
          return (
            <div key={r.key} style={{ flex: r.value, background: colors[i], color: inks[i] }} title={`${r.key}: ${r.value}`}>
              {r.value}
            </div>
          );
        })}
      </div>
      <Legend items={rows.map((r, i) => [colors[i], r.key, "box"])} />
    </>
  );
}

/** Retention heat: the kit's ramp steps, fixed so a week reads the same everywhere. */
export function Heat({ cohorts }: { cohorts: { label: string; pct: (number | null)[] }[] }) {
  const weeks = 6;
  const tone = (v: number): [string, string] =>
    v >= 90 ? [RAMP[5], "#FFF"] : v >= 55 ? [RAMP[4], "#FFF"] : v >= 45 ? [RAMP[3], "#FFF"] : v >= 38 ? [RAMP[2], "#0E1116"] : v >= 34 ? [RAMP[1], "#0E1116"] : [RAMP[0], "#0E1116"];
  return (
    <div className="heat">
      <span />
      {Array.from({ length: weeks }, (_, w) => (
        <span className="hh" key={w}>
          Week {w}
        </span>
      ))}
      {cohorts.map((c) => (
        <HeatRow key={c.label} label={c.label} pct={c.pct.slice(0, weeks)} tone={tone} weeks={weeks} />
      ))}
    </div>
  );
}

const HeatRow = ({ label, pct, tone, weeks }: { label: string; pct: (number | null)[]; tone: (v: number) => [string, string]; weeks: number }) => (
  <>
    <span className="hl">{label}</span>
    {Array.from({ length: weeks }, (_, i) => {
      const v = pct[i];
      if (v == null) return <span className="hc empty" key={i} />;
      const [bg, fg] = tone(v);
      return (
        <span className="hc" key={i} style={{ background: bg, color: fg }}>
          {v}%
        </span>
      );
    })}
  </>
);

export const Meter = ({ value, max, label, color = "#0B6E4F" }: { value: number; max: number; label: ReactNode; color?: string }) => (
  <>
    <div className="meter" role="meter" aria-valuenow={value} aria-valuemin={0} aria-valuemax={max}>
      <div style={{ width: `${Math.min(100, (value / Math.max(1, max)) * 100).toFixed(1)}%`, background: color }} />
    </div>
    <span className="sub">{label}</span>
  </>
);

export function Spark({ values, color = "var(--c0)", width = 96, height = 26 }: { values: number[]; color?: string; width?: number; height?: number }) {
  if (values.length < 2) return <svg width={width} height={height} />;
  const max = Math.max(...values);
  const min = Math.min(...values);
  const step = width / (values.length - 1);
  const points = values.map((v, i) => `${(i * step).toFixed(1)},${(height - 2 - ((v - min) / (max - min || 1)) * (height - 4)).toFixed(1)}`).join(" ");
  return (
    <svg width={width} height={height} style={{ display: "block" }} aria-hidden="true">
      <polyline points={points} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" />
    </svg>
  );
}

/** Days-active calendar (board U4): one square per day, Monday rows first, oldest column left. */
export function Calendar({ activeDays, weeks = 26, today }: { activeDays: Set<string>; weeks?: number; today: string }) {
  const end = new Date(`${today}T00:00:00Z`);
  const dow = (end.getUTCDay() + 6) % 7;
  const start = new Date(end);
  start.setUTCDate(end.getUTCDate() - dow - (weeks - 1) * 7);
  const cells: { d: string; on: boolean; future: boolean }[] = [];
  for (let i = 0; i < weeks * 7; i++) {
    const t = new Date(start);
    t.setUTCDate(start.getUTCDate() + i);
    const d = t.toISOString().slice(0, 10);
    cells.push({ d, on: activeDays.has(d), future: d > today });
  }
  return (
    <div className="cal" role="img" aria-label={`${activeDays.size} active days`}>
      {cells.map((c) => (
        <i key={c.d} className={c.on ? "l4" : undefined} title={c.future ? undefined : `${dayLabel(c.d)}${c.on ? ": active" : ""}`} style={c.future ? { visibility: "hidden" } : undefined} />
      ))}
    </div>
  );
}
