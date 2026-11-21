import { describe, expect, it } from "vitest";
import { buildOverview, healthChecks } from "@/lib/overview";
import type { Health, History, Today } from "@/lib/types";

const today: Today = {
  live: {
    day: "2026-11-21",
    today: { signUps: 2, leagues: 1, joins: 4, reports: 0, otpSends: 9, bids: 30, activeUsers: 50 },
    now: { liveAuctions: 2, bidsLastMinute: 8, auctionViewers: 49 },
    feed: [],
  },
  moderation: { openReports: 3, oldestOpenAt: "2026-11-20T10:00:00Z", openByReason: { UNDER_18: 1, SOMETHING_ELSE: 2 }, suspendedAccounts: 1, imagesRemoved30d: 0 },
};

const history: History = {
  from: "2026-08-23",
  to: "2026-11-20",
  markers: [],
  rows: [
    { day: "2026-11-20", metric: "users_total", dimension: "", value: 1000 },
    { day: "2026-11-20", metric: "active_users_month", dimension: "", value: 200 },
    { day: "2026-11-20", metric: "new_users", dimension: "", value: 30 },
    { day: "2026-10-10", metric: "new_users", dimension: "", value: 10 },
    { day: "2026-11-15", metric: "otp_people_sent", dimension: "", value: 100 },
    { day: "2026-11-15", metric: "otp_people_signed_in", dimension: "", value: 80 },
    { day: "2026-11-15", metric: "cohort_profile_done", dimension: "", value: 70 },
    { day: "2026-11-15", metric: "cohort_joined", dimension: "", value: 35 },
    { day: "2026-11-20", metric: "report_close_median_minutes_30d", dimension: "", value: 360 },
    { day: "2026-11-19", metric: "reports_closed", dimension: "", value: 2 },
  ],
};

const text = (parts: { t: string }[]) => parts.map((p) => p.t).join("");

describe("buildOverview", () => {
  const o = buildOverview(history, today, null, "30", "2026-11-20", false);

  it("adds today's sign-ups to the stored total", () => {
    expect(o.kpis[0].value).toBe("1,002");
    expect(o.kpis[0].delta).toBe("+30 new");
    // 10 before is under 20, so the change is a count, not a percentage.
    expect(o.kpis[0].sub).toBe("+20 vs previous 30 days");
  });

  it("writes summaries from the same numbers as the charts", () => {
    expect(text(o.sentences.live)).toBe("2 auctions are running with 49 people watching. Bids are coming in at 8 a minute.");
    expect(text(o.sentences.growth)).toContain("30 people joined the last 30 days, 20 more than the 30 days before.");
    expect(text(o.sentences.growth)).toContain("The biggest drop is after profile done: 50% of people at that step go on to joined a league.");
    expect(text(o.sentences.engagement)).toBe("50 people used Crichere today. About 1 in 4 monthly users comes back on a given day.");
    expect(text(o.sentences.trust)).toBe("Reports close in a median of 6 hours, inside the 24-hour target. 3 are open now.");
  });

  it("keeps only figures bold", () => {
    const bold = o.sentences.live.filter((p) => p.b).map((p) => p.t);
    expect(bold).toEqual(["2 auctions", "49 people", "8"]);
  });

  it("orders the funnel and open reasons", () => {
    expect(o.funnel.map((f) => f.value)).toEqual([100, 80, 70, 35]);
    expect(o.openByReason[0]).toEqual({ key: "SOMETHING_ELSE", value: 2 });
  });
});

describe("healthChecks", () => {
  const base: Health = {
    checkedAt: new Date().toISOString(),
    nightly: { status: "SUCCEEDED", startedAt: new Date(Date.now() - 3600_000).toISOString(), finishedAt: new Date(Date.now() - 3559_000).toISOString(), detail: null },
    otp: { codesSentToday: 100, expiredUnusedToday: 10, failureRate: 0.1 },
    sms: { sentToday: 214, dailyCap: 2000, resetsAt: "" },
    msg91Balance: null,
    ses: { configured: true, deliveryAttempts14d: 20, bounces14d: 0, complaints14d: 0 },
  };

  it("is all clear on a normal day", () => {
    expect(healthChecks(base).every((h) => h.ok)).toBe(true);
  });

  it("flags a failed run, a high code failure rate, the SMS cap and bounces", () => {
    const bad = healthChecks({
      ...base,
      nightly: { ...base.nightly, status: "FAILED", detail: "timeout" },
      otp: { codesSentToday: 100, expiredUnusedToday: 40, failureRate: 0.4 },
      sms: { ...base.sms, sentToday: 1900 },
      ses: { ...base.ses, bounces14d: 1 },
    });
    expect(bad.filter((h) => !h.ok).map((h) => h.name)).toEqual(["Nightly summary", "Sign-in codes", "SMS today", "Alert emails"]);
  });
});
