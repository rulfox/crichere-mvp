import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { LiveAuction } from "@/components/LiveAuction";
import { fetchLeague } from "@/lib/api";

type Props = {
  params: Promise<{ id: string }>;
};

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params;
  const league = await fetchLeague(id);
  if (!league) return {};

  const description = `Follow ${league.name}'s live player auction in ${league.city} -- no account needed.`;
  const image = league.bannerUrl ?? league.logoUrl;

  return {
    title: league.name,
    description,
    openGraph: {
      title: league.name,
      description,
      images: image ? [image] : undefined,
    },
  };
}

export default async function LeaguePage({ params }: Props) {
  const { id } = await params;
  const league = await fetchLeague(id);
  if (!league) notFound();

  return <LiveAuction league={league} />;
}
