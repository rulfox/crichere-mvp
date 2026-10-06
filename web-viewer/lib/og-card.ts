/**
 * Pure rules behind the per-league share card (`app/leagues/[id]/opengraph-image.tsx`), lifted
 * verbatim from the Claude Design spec "Crichere League Share Card" (docs/PHASE13.md) so they can
 * be unit-tested without rendering an image.
 */

/** Where league logos are uploaded (PHASE14). The share card fetches from nowhere else. */
const DEFAULT_LOGO_ORIGINS = ["https://crichere-media-prod.s3.ap-south-1.amazonaws.com"];

/**
 * Origins the share card may fetch a logo from: `SHARE_CARD_LOGO_ORIGINS` (comma-separated, for
 * tests and other environments) or the production media bucket.
 */
export function logoOrigins(configured: string | undefined = process.env.SHARE_CARD_LOGO_ORIGINS): string[] {
  const list = (configured ?? "").split(",").map((origin) => origin.trim()).filter(Boolean);
  return list.length > 0 ? list : DEFAULT_LOGO_ORIGINS;
}

/**
 * The card fetches the logo from this server, inside Railway's private network, and the URL comes
 * from the organizer. So only an exact allowed origin is fetched: never an internal host, another
 * scheme, or a look-alike (docs/SECURITY-AUDIT.md, below the bar).
 */
export function isFetchableLogoUrl(url: string, origins: string[]): boolean {
  try {
    return origins.includes(new URL(url).origin);
  } catch {
    return false;
  }
}

const MONOGRAM_SKIP =new Set(["the", "cricket", "league", "premier", "club", "cc"]);

/** Initials of the first two words, skipping filler words; one letter if only one word is left. */
export function monogram(name: string): string {
  const words = name
    .split(/[^\p{L}\p{N}]+/u)
    .filter((word) => word.length > 0 && !MONOGRAM_SKIP.has(word.toLowerCase()));
  // A name made only of skipped words ("Cricket League") still needs a letter.
  const source = words.length > 0 ? words : name.split(/\s+/).filter(Boolean);
  return source
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join("");
}

/** League-name size by length: <=22 chars 80px, 23-40 64px, 41+ 52px. */
export function nameFontSize(name: string): number {
  const length = [...name].length;
  if (length <= 22) return 80;
  if (length <= 40) return 64;
  return 52;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

const istParts = new Intl.DateTimeFormat("en-IN", {
  timeZone: "Asia/Kolkata",
  year: "numeric",
  month: "numeric",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

/**
 * "14 Nov 2026, 7:30 PM", always in India time: the card is rendered once on the server and seen by
 * everyone, so it can't follow a viewer's zone the way the live page's `formatScheduled` does.
 */
export function formatAuctionDate(iso: string): string {
  const parts = Object.fromEntries(istParts.formatToParts(new Date(iso)).map((part) => [part.type, part.value]));
  return `${parts.day} ${MONTHS[Number(parts.month) - 1]} ${parts.year}, ${parts.hour}:${parts.minute} ${(parts.dayPeriod ?? "").toUpperCase()}`;
}

/** W11 (design update #6): "District, State". */
export function locationLine(district: string, state: string): string {
  return [district, state].filter((part) => part && part.trim().length > 0).join(", ");
}

export type CardStatus = { kind: "live" } | { kind: "scheduled"; label: string } | { kind: "none" };

/** LIVE wins; otherwise a scheduled time still in the future; otherwise nothing. */
export function cardStatus(isLive: boolean, auctionScheduledAt: string | null | undefined, now: Date = new Date()): CardStatus {
  if (isLive) return { kind: "live" };
  if (auctionScheduledAt && new Date(auctionScheduledAt).getTime() > now.getTime()) {
    return { kind: "scheduled", label: formatAuctionDate(auctionScheduledAt) };
  }
  return { kind: "none" };
}
