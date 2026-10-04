import type { Metadata } from "next";
import { LegalArticle, type LegalSection } from "@/components/legal/LegalArticle";

/*
 * PLACEHOLDER TEXT. The layout is final (design update #5 W8a); the copy is not. Kept out of search
 * and out of the footer until the owner supplies the real policy (docs/PHASE15.md 8.3).
 */
export const metadata: Metadata = {
  title: "Privacy Policy",
  alternates: { canonical: "/privacy" },
  robots: { index: false, follow: true },
};

const SECTIONS: LegalSection[] = [
  {
    id: "information-we-collect",
    title: "1. Information we collect",
    body: (
      <>
        <p>Body paragraph placeholder. Your text goes here, one idea per paragraph, at a comfortable reading length.</p>
        <h3>1.1 Account details</h3>
        <ul>
          <li>List item placeholder</li>
          <li>
            <span>
              List item placeholder with a <a href="#how-we-use-it">link</a>
            </span>
          </li>
        </ul>
      </>
    ),
  },
  { id: "how-we-use-it", title: "2. How we use it", body: <p>Body paragraph placeholder.</p> },
  { id: "sharing", title: "3. Who we share it with", body: <p>Body paragraph placeholder.</p> },
  { id: "your-choices", title: "4. Your choices", body: <p>Body paragraph placeholder.</p> },
  { id: "contact", title: "5. Contact", body: <p>Body paragraph placeholder.</p> },
];

export default function PrivacyPage() {
  return (
    <LegalArticle
      page="privacy"
      title="Privacy Policy"
      updated="2026-10-04"
      intro="Intro paragraph. A short summary of what this page covers and who it applies to, written in plain language."
      sections={SECTIONS}
    />
  );
}
