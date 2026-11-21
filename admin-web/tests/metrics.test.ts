import { describe, expect, it } from "vitest";
import { M, Metrics, mondayOf, topAndRest } from "@/lib/metrics";

const rows = [
  { day: "2026-11-18", metric: M.newUsers, dimension: "", value: 2 },
  { day: "2026-11-20", metric: M.newUsers, dimension: "", value: 5 },
  { day: "2026-11-19", metric: M.usersByDistrict, dimension: "Kerala|Thrissur", value: 3 },
  { day: "2026-11-20", metric: M.usersByDistrict, dimension: "Kerala|Alappuzha", value: 9 },
  { day: "2026-11-20", metric: M.usersByDistrict, dimension: "Kerala|Ernakulam", value: 4 },
  { day: "2026-11-02", metric: M.retentionSize, dimension: "", value: 10 },
  { day: "2026-11-02", metric: M.retentionWeek, dimension: "w0", value: 10 },
  { day: "2026-11-02", metric: M.retentionWeek, dimension: "w1", value: 6 },
];

describe("Metrics", () => {
  const m = new Metrics(rows);

  it("fills missing days with 0", () => {
    expect(m.series(M.newUsers, "2026-11-18", "2026-11-20").map((p) => p.value)).toEqual([2, 0, 5]);
    expect(m.sum(M.newUsers, "2026-11-18", "2026-11-20")).toBe(7);
  });

  it("reads a split from its latest day only", () => {
    expect(m.split(M.usersByDistrict, "2026-11-21")).toEqual([
      { key: "Kerala|Alappuzha", value: 9 },
      { key: "Kerala|Ernakulam", value: 4 },
    ]);
  });

  it("turns retention counts into percentages and leaves future weeks empty", () => {
    const [c] = m.retention(6, "2026-11-12");
    expect(c.size).toBe(10);
    expect(c.pct).toEqual([100, 60, null, null, null, null]);
  });

  it("finds Mondays and groups the rest", () => {
    expect(mondayOf("2026-11-21")).toBe("2026-11-16");
    expect(
      topAndRest(
        [
          { key: "a", value: 5 },
          { key: "b", value: 3 },
          { key: "c", value: 2 },
        ],
        1,
        "Other",
      ),
    ).toEqual([
      { key: "a", value: 5 },
      { key: "Other", value: 5 },
    ]);
  });
});
