import type { Metadata } from "next";
import { Archivo, Instrument_Sans, JetBrains_Mono } from "next/font/google";
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

export const metadata: Metadata = {
  title: {
    template: "%s | Crichere",
    default: "Crichere -- Build. Auction. Compete.",
  },
  description: "The ultimate platform to organize and manage cricket leagues, with live player auctions included.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${displayFont.variable} ${bodyFont.variable} ${monoFont.variable}`}>
      <body>{children}</body>
    </html>
  );
}
