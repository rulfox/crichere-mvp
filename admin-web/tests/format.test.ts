import { describe, expect, it } from "vitest";
import { addDays, age, change, initials, num, reasonLabel, reasonTone, todayIst } from "@/lib/format";

describe("format", () => {
  it("groups digits the Indian way", () => {
    expect(num(1234567)).toBe("12,34,567");
    expect(num(null)).toBe("—");
  });

  it("says how long something waited", () => {
    const now = Date.parse("2026-11-21T12:00:00Z");
    expect(age("2026-11-21T11:18:00Z", now)).toBe("42 min");
    expect(age("2026-11-20T17:00:00Z", now)).toBe("19 h");
    expect(age("2026-11-18T12:00:00Z", now)).toBe("3 d");
  });

  it("uses counts, not percentages, under 20 (board X6)", () => {
    expect(change(4, 3)).toBe("+1");
    expect(change(3, 4)).toBe("−1");
    expect(change(150, 100)).toBe("+50%");
    expect(change(100, 100)).toBe("±0%");
  });

  it("takes today in India, not UTC", () => {
    expect(todayIst(new Date("2026-11-20T19:00:00Z"))).toBe("2026-11-21");
    expect(addDays("2026-11-30", 1)).toBe("2026-12-01");
  });

  it("labels reasons and colours the urgent ones", () => {
    expect(reasonLabel("UNDER_18")).toBe("Under 18");
    expect(reasonTone("UNDER_18")).toBe("danger");
    expect(reasonTone("SPAM_OR_MISLEADING")).toBe("warn");
    expect(initials("Nikhil  das")).toBe("ND");
  });
});
