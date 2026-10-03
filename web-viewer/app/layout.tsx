import type { Metadata } from "next";
import { Archivo, Instrument_Sans, JetBrains_Mono } from "next/font/google";
import { DEFAULT_OG_IMAGE, SITE_NAME, SITE_URL } from "@/lib/seo";
import "./globals.css";

const displayFont = Archivo({
  variable: "--font-display",
  subsets: ["latin"],
  weight: ["500", "700", "800"],
});

const bodyFont = Instrument_Sans({
  variable: "--font-body",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
});

const monoFont = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500", "700"],
  // JetBrains Mono has no ₹ (U+20B9), so every amount's rupee sign comes from the fallback. The
  // design falls back to plain `monospace`; next/font's default size-adjusted Arial fallback
  // would draw a visibly wider, heavier ₹ instead.
  adjustFontFallback: false,
  fallback: ["monospace"],
});

const description = "The ultimate platform to organize and manage cricket leagues, with live player auctions included.";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    template: "%s | Crichere",
    default: "Crichere -- Build. Auction. Compete.",
  },
  description,
  alternates: { canonical: "/" },
  // Pages that set their own `openGraph` replace this object wholesale (Next merges metadata
  // shallowly), so league pages repeat siteName/type themselves -- see app/leagues/[id]/page.tsx.
  openGraph: {
    siteName: SITE_NAME,
    type: "website",
    url: "/",
    title: "Crichere -- Build. Auction. Compete.",
    description,
    images: [DEFAULT_OG_IMAGE],
  },
  twitter: {
    card: "summary_large_image",
    images: [DEFAULT_OG_IMAGE.url],
  },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${displayFont.variable} ${bodyFont.variable} ${monoFont.variable}`}>
      <body>{children}</body>
    </html>
  );
}
