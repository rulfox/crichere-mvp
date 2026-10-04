import type { Metadata } from "next";
import { LegalArticle, type LegalSection } from "@/components/legal/LegalArticle";

/*
 * PLACEHOLDER TEXT. The layout is final (design update #5 W8b); the copy is not. Kept out of search
 * and out of the footer until the owner supplies the real terms (docs/PHASE15.md 8.3).
 */
export const metadata: Metadata = {
  title: "Terms of Service",
  alternates: { canonical: "/terms" },
  robots: { index: false, follow: true },
};

const SECTIONS: LegalSection[] = [
  {
    id: "using-crichere",
    title: "1. Using Crichere",
    body: (
      <>
        <p>
          Body paragraph placeholder. Your text goes here, one idea per paragraph. The measure is capped at 680 px (about 75 characters) so long
          sections stay readable.
        </p>
        <p>
          Second paragraph placeholder, with an inline <a href="#accounts">link to another section</a> to show link styling in running text.
        </p>
        <h3>1.1 Who can use it</h3>
        <ul>
          <li>List item placeholder</li>
          <li>List item placeholder that runs long enough to wrap onto a second line, aligned under the text</li>
        </ul>
        <ol>
          <li>Ordered item placeholder</li>
        </ol>
      </>
    ),
  },
  { id: "accounts", title: "2. Accounts", body: <p>Body paragraph placeholder.</p> },
  { id: "leagues-and-auctions", title: "3. Leagues and auctions", body: <p>Body paragraph placeholder.</p> },
  { id: "payments", title: "4. Payments between users", body: <p>Body paragraph placeholder.</p> },
  { id: "contact", title: "5. Contact", body: <p>Body paragraph placeholder.</p> },
];

export default function TermsPage() {
  return (
    <LegalArticle
      page="terms"
      title="Terms of Service"
      updated="2026-10-04"
      intro="Intro paragraph. A short summary of what these terms cover and who they apply to, written in plain language before the detail starts."
      sections={SECTIONS}
    />
  );
}
