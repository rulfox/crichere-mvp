import { describe, expect, it } from "vitest";
import { leagueShareDescription, truncate } from "./seo";

describe("truncate", () => {
  it("leaves short text alone and collapses whitespace", () => {
    expect(truncate("  Weekend   league\n in Kochi ", 50)).toBe("Weekend league in Kochi");
  });

  it("cuts at a word boundary and adds an ellipsis", () => {
    const result = truncate("The quick brown fox jumps over the lazy dog", 20);
    expect(result).toBe("The quick brown fox…");
    expect([...result].length).toBeLessThanOrEqual(20);
  });
});

describe("leagueShareDescription", () => {
  it("uses the organizer's description when present", () => {
    expect(leagueShareDescription({ name: "Kochi Super Sixes", city: "Kochi", description: "Six teams, one night." })).toBe(
      "Six teams, one night.",
    );
  });

  it("falls back to a line naming the league and city", () => {
    expect(leagueShareDescription({ name: "Kochi Super Sixes", city: "Kochi", description: "  " })).toBe(
      "Follow Kochi Super Sixes's live player auction in Kochi -- no account needed.",
    );
    expect(leagueShareDescription({ name: "Kochi Super Sixes ", city: "Kochi" })).toBe(
      "Follow Kochi Super Sixes's live player auction in Kochi -- no account needed.",
    );
  });
});
