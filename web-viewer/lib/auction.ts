/** Pure view-state/formatting helpers, kept out of components so they're unit-testable without a DOM (docs/PHASE6.md). */

const currency = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

/** Real Indian Rupee grouping (e.g. ₹1,00,000), not a generic `$`/US-grouping default. */
export function formatInr(amount: string | number): string {
  return currency.format(Number(amount));
}

const clock = new Intl.DateTimeFormat("en-IN", { hour: "numeric", minute: "2-digit", second: "2-digit" });

export function formatBidTime(iso: string): string {
  return clock.format(new Date(iso));
}

export type ViewState = "not-started" | "live" | "completed";

export function viewStateFor(auctionStatus: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED"): ViewState {
  switch (auctionStatus) {
    case "NOT_STARTED":
      return "not-started";
    case "IN_PROGRESS":
      return "live";
    case "COMPLETED":
      return "completed";
  }
}
