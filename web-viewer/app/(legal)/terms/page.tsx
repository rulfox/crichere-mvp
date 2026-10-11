import type { Metadata } from "next";
import Link from "next/link";
import { LegalArticle, type LegalSection } from "@/components/legal/LegalArticle";

/*
 * Draft terms from docs/legal/TERMS-OF-SERVICE.md (keep the two in step). The [bracketed] items still
 * need the owner's details, so the page stays out of search and out of the footer until they are
 * filled in and reviewed (docs/PHASE16.md).
 */
export const metadata: Metadata = {
  title: "Terms of Service",
  alternates: { canonical: "/terms" },
  robots: { index: false, follow: true },
};

const SECTIONS: LegalSection[] = [
  {
    id: "what-crichere-is",
    title: "1. What Crichere is",
    body: (
      <p>
        Crichere is software for amateur cricket leagues. Organisers create leagues, run a live player auction and manage franchises; players create
        profiles, join leagues and are bid for; franchise owners bid for players with a virtual purse. Auction purses, bids and prices are virtual
        points used to run the auction. They are not real money and have no cash value.
      </p>
    ),
  },
  {
    id: "accounts",
    title: "2. Eligibility and account",
    body: (
      <ul>
        <li>You must be 18 or older, live in India, and be able to enter a binding contract.</li>
        <li>Sign-in is by OTP to your own mobile number. Give accurate information and keep it current.</li>
        <li>You are responsible for activity on your account. Tell us promptly at hello@crichere.com if you suspect misuse.</li>
        <li>One person, one account. Do not impersonate others or use another person&rsquo;s number.</li>
      </ul>
    ),
  },
  {
    id: "your-content",
    title: "3. Your content",
    body: (
      <>
        <p>
          You keep ownership of what you upload (photos, logos, banners, league and ground details, payment screenshots). You give us a
          non-exclusive, worldwide, royalty-free licence to host, display, copy and process that content only to run and promote the Service,
          including league pages, share cards and links. The licence ends when you delete the content or your account, except for short-lived
          backups and content already part of completed leagues&rsquo; records.
        </p>
        <p>You confirm you have the rights to what you upload, and that it does not break any law or anyone&rsquo;s rights.</p>
      </>
    ),
  },
  {
    id: "acceptable-use",
    title: "4. Acceptable use",
    body: (
      <>
        <p>Do not:</p>
        <ul>
          <li>upload unlawful, obscene, hateful, harassing or misleading content, or images of other people without their permission;</li>
          <li>use the Service for betting, gambling, wagering or any real-money game of chance, or sell auction slots for money outside a league&rsquo;s rules;</li>
          <li>manipulate auctions, place bids you do not intend to honour in your league, use bots, or interfere with other users&rsquo; bids;</li>
          <li>scrape, reverse-engineer, overload, probe or attack the Service, or bypass security or rate limits;</li>
          <li>collect other users&rsquo; data or use it for anything other than the league it was shown for;</li>
          <li>break any applicable law, including the Information Technology Act, 2000 and its rules.</li>
        </ul>
        <p>We may remove content, hide a league, and suspend or end accounts that break these Terms or harm others.</p>
      </>
    ),
  },
  {
    id: "leagues-and-fees",
    title: "5. Organisers and league fees",
    body: (
      <ul>
        <li>An organiser runs their league independently. Crichere is not a party to a league and does not organise, insure or supervise matches, venues, teams or events.</li>
        <li>
          Joining fees, franchise fees and prizes are agreed directly between participants and the organiser. Crichere does not receive, hold,
          transfer or refund money and is not a payment service. Payment screenshots are only proof shown to the organiser.
        </li>
        <li>
          Refunds, fee disputes, team selection and rule decisions are for the organiser and participants to resolve. Organisers must publish their
          rules and fees clearly and use participants&rsquo; data only to run the league.
        </li>
        <li>Playing cricket carries a risk of injury. You take part at your own risk and are responsible for your own safety, equipment and fitness.</li>
      </ul>
    ),
  },
  {
    id: "third-parties",
    title: "6. Location, notifications and third parties",
    body: (
      <p>
        Features that use your approximate location, notifications, Google Maps and Places, SMS OTP (MSG91) and cloud hosting depend on third-party
        services with their own terms. We are not responsible for their availability or conduct. Details are in the{" "}
        <Link href="/privacy">Privacy Policy</Link>.
      </p>
    ),
  },
  {
    id: "intellectual-property",
    title: "7. Intellectual property",
    body: (
      <p>
        The Service, the Crichere name, logo, design and software belong to us or our licensors. We grant you a limited, revocable, non-transferable
        licence to use the app for personal, lawful use.
      </p>
    ),
  },
  {
    id: "availability",
    title: "8. Availability and changes",
    body: (
      <p>
        The Service may change, be interrupted or be discontinued, and we may add or remove features. We will try to give notice of material changes
        but do not promise uninterrupted or error-free service, or that live auctions will never be disrupted by network or device problems.
      </p>
    ),
  },
  {
    id: "liability",
    title: "9. Disclaimers and liability",
    body: (
      <>
        <p>To the fullest extent permitted by law:</p>
        <ul>
          <li>the Service is provided &ldquo;as is&rdquo; and &ldquo;as available&rdquo; without warranties of any kind;</li>
          <li>we are not liable for the acts of organisers, franchise owners, players or other users, disputes between them, or money exchanged between them;</li>
          <li>we are not liable for indirect, incidental, special or consequential loss, or loss of profit, data or goodwill;</li>
          <li>our total liability to you for any claim relating to the Service is limited to Rs. [1,000].</li>
        </ul>
        <p>Nothing in these Terms excludes liability that cannot be excluded under Indian law.</p>
      </>
    ),
  },
  {
    id: "indemnity",
    title: "10. Indemnity",
    body: (
      <p>
        You will indemnify us against claims, losses and costs (including reasonable legal fees) arising from your content, your breach of these
        Terms or law, or your dealings with other users.
      </p>
    ),
  },
  {
    id: "termination",
    title: "11. Termination and account deletion",
    body: (
      <p>
        You may stop using the Service at any time and delete your account as described in <Link href="/delete-account">Delete your account</Link>.
        We may suspend or end your access as set out in section 4. Sections that by their nature should survive (content licence for completed
        records, disclaimers, liability, indemnity, governing law) survive termination.
      </p>
    ),
  },
  {
    id: "changes",
    title: "12. Changes to these Terms",
    body: (
      <p>
        We may update these Terms and will notify you in the app or by push for material changes. If you keep using the Service after the effective
        date, you accept the new Terms. If you disagree, stop using the Service and delete your account.
      </p>
    ),
  },
  {
    id: "governing-law",
    title: "13. Governing law and disputes",
    body: (
      <p>
        These Terms are governed by the laws of India. Courts at [City, State], India have exclusive jurisdiction, subject to any mandatory consumer
        rights. Please first write to hello@crichere.com so we can try to resolve the issue informally.
      </p>
    ),
  },
  {
    id: "contact",
    title: "14. Grievance Officer and contact",
    body: (
      <p>
        Grievance Officer: [Name], <a href="mailto:hello@crichere.com">hello@crichere.com</a>, [registered address]. We acknowledge complaints within
        24 hours and resolve them within 15 days.
      </p>
    ),
  },
];

export default function TermsPage() {
  return (
    <LegalArticle
      page="terms"
      title="Terms of Service"
      updated="2026-10-11"
      intro="These Terms are a contract between you and [Legal name], India, for the Crichere app and crichere.com. By creating an account or using the Service you accept them and our Privacy Policy."
      sections={SECTIONS}
    />
  );
}
