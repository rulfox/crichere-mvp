import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { LiveAuction } from "@/components/LiveAuction";
import { fetchLeague } from "@/lib/api";
import { SITE_NAME, leagueShareDescription } from "@/lib/seo";

type Props = {
  params: Promise<{ id: string }>;
};

/**
 * Link-preview tags for a shared league (docs/PHASE13.md). The image is not set here: the
 * colocated `opengraph-image.tsx` renders the league's own card, and file-based metadata adds
 * og:image (with width/height) on top of whatever this returns.
 */
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const league = await fetchLeague(id);
  if (!league) return {};

  const name = league.name.trim();
  const description = leagueShareDescription(league);
  const path = `/leagues/${league.id}`;

  return {
    title: name,
    description,
    alternates: { canonical: path },
    openGraph: {
      siteName: SITE_NAME,
      type: "website",
      url: path,
      title: name,
      description,
    },
    twitter: {
      card: "summary_large_image",
      title: name,
      description,
    },
  };
}

export default async function LeaguePage({ params }: Props) {
  const { id } = await params;
  const league = await fetchLeague(id);
  if (!league) notFound();

  return <LiveAuction league={league} />;
}
