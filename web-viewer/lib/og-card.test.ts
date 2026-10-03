import { describe, expect, it } from "vitest";
import { cardStatus, formatAuctionDate, locationLine, monogram, nameFontSize } from "./og-card";

describe("monogram", () => {
  it("takes the first letters of the first two words", () => {
    expect(monogram("Thar Strikers")).toBe("TS");
    expect(monogram("Brahmaputra Valley Weekend Tennis-Ball Cricket Championship")).toBe("BV");
  });

  it("skips The / Cricket / League / Premier / Club / CC", () => {
    expect(monogram("The Sahyadri Amateur League")).toBe("SA");
    expect(monogram("Kochi Cricket Club")).toBe("K");
    expect(monogram("Premier League of Ponnani")).toBe("OP");
  });

  it("uses one letter when only one word is left", () => {
    expect(monogram("Spartanz Premier League")).toBe("S");
  });

  it("still returns a letter when every word is skipped", () => {
    expect(monogram("Cricket League")).toBe("CL");
  });
});

describe("nameFontSize", () => {
  it("follows the design's three length bands", () => {
    expect(nameFontSize("Kochi Super Sixes")).toBe(80);
    expect(nameFontSize("x".repeat(22))).toBe(80);
    expect(nameFontSize("x".repeat(23))).toBe(64);
    expect(nameFontSize("Pune Weekend Warriors League")).toBe(64);
    expect(nameFontSize("x".repeat(40))).toBe(64);
    expect(nameFontSize("x".repeat(41))).toBe(52);
  });
});

describe("formatAuctionDate", () => {
  it("formats in India time as '14 Nov 2026, 7:30 PM'", () => {
    expect(formatAuctionDate("2026-11-14T14:00:00Z")).toBe("14 Nov 2026, 7:30 PM");
    expect(formatAuctionDate("2027-01-02T12:30:00Z")).toBe("2 Jan 2027, 6:00 PM");
  });
});

describe("locationLine", () => {
  it("joins city and state, dropping blanks", () => {
    expect(locationLine("Kochi", "Kerala")).toBe("Kochi, Kerala");
    expect(locationLine("Kochi", "")).toBe("Kochi");
  });
});

describe("cardStatus", () => {
  const now = new Date("2026-10-03T00:00:00Z");

  it("is LIVE when the auction is live, even with a schedule", () => {
    expect(cardStatus(true, "2026-11-14T14:00:00Z", now)).toEqual({ kind: "live" });
  });

  it("shows a future schedule", () => {
    expect(cardStatus(false, "2026-11-14T14:00:00Z", now)).toEqual({ kind: "scheduled", label: "14 Nov 2026, 7:30 PM" });
  });

  it("shows nothing for a past or missing schedule", () => {
    expect(cardStatus(false, "2026-09-01T14:00:00Z", now)).toEqual({ kind: "none" });
    expect(cardStatus(false, null, now)).toEqual({ kind: "none" });
  });
});
