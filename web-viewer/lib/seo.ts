/**
 * Link-preview (Open Graph) settings shared by the root layout and league pages (docs/PHASE13.md).
 * Chat apps and social sites read these tags once, without running JS, and cache the result per
 * URL -- so every page must carry an absolute https image and a meaningful description.
 */

export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://crichere.com";

export const SITE_NAME = "Crichere";

/** The approved Claude Design default card ("Crichere OG Card", 1200x630). */
export const DEFAULT_OG_IMAGE = {
  url: "/og/crichere-default.png",
  width: 1200,
  height: 630,
  alt: "Crichere -- run your cricket league. Live.",
};

export function truncate(text: string, max: number): string {
  const clean = text.replace(/\s+/g, " ").trim();
  if ([...clean].length <= max) return clean;
  const chars = [...clean];
  const cut = chars.slice(0, max - 1).join("");
  // Only drop the trailing partial word -- a cut that lands right before a space is already clean.
  const atWord = /\s/.test(chars[max - 1]) ? cut : cut.replace(/\s+\S*$/, "");
  return `${(atWord.length > max / 2 ? atWord : cut).trimEnd()}…`;
}

/** The organizer's own description when there is one, else a generic line naming the league. */
export function leagueShareDescription(league: { name: string; city: string; description?: string | null }): string {
  if (league.description && league.description.trim().length > 0) return truncate(league.description, 160);
  return `Follow ${league.name.trim()}'s live player auction in ${league.city.trim()} -- no account needed.`;
}
