import { describe, expect, it } from "vitest";
import { cardStatus, formatAuctionDate, isFetchableLogoUrl, locationLine, logoOrigins, monogram, nameFontSize } from "./og-card";

describe("logo fetch allowlist", () => {
  const bucket = "https://crichere-media-prod.s3.ap-south-1.amazonaws.com";
  const origins = logoOrigins(undefined);

  it("defaults to the production media bucket", () => {
    expect(origins).toEqual([bucket]);
  });

  it("reads a comma-separated override", () => {
    expect(logoOrigins(" http://localhost:4310 , https://cdn.example ")).toEqual(["http://localhost:4310", "https://cdn.example"]);
  });

  it("allows a logo in the media bucket", () => {
    expect(isFetchableLogoUrl(`${bucket}/leagues/abc/logo.jpg?v=1`, origins)).toBe(true);
  });

  it.each([
    ["an internal Railway host", "http://backend.railway.internal:8080/api/v1/leagues"],
    ["the bucket over plain http", "http://crichere-media-prod.s3.ap-south-1.amazonaws.com/leagues/abc/logo.jpg"],
    ["a look-alike host", "https://crichere-media-prod.s3.ap-south-1.amazonaws.com.evil.test/logo.jpg"],
    ["userinfo pointing elsewhere", "https://crichere-media-prod.s3.ap-south-1.amazonaws.com@evil.test/logo.jpg"],
    ["the bucket on another port", "https://crichere-media-prod.s3.ap-south-1.amazonaws.com:8443/logo.jpg"],
    ["a link-local metadata address", "http://169.254.169.254/latest/meta-data/"],
    ["a file URL", "file:///etc/passwd"],
    ["not a URL", "logo.jpg"],
  ])("refuses %s", (_, url) => {
    expect(isFetchableLogoUrl(url, origins)).toBe(false);
  });
});

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
  it("joins district and state, dropping blanks", () => {
    expect(locationLine("Ernakulam", "Kerala")).toBe("Ernakulam, Kerala");
    expect(locationLine("Ernakulam", "")).toBe("Ernakulam");
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
