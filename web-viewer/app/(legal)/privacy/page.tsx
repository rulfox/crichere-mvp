import type { Metadata } from "next";
import Link from "next/link";
import { LegalArticle, type LegalSection } from "@/components/legal/LegalArticle";

/*
 * Draft policy text from docs/legal/PRIVACY-POLICY.md (keep the two in step). The [bracketed] items
 * still need the owner's details, so the page stays out of search and out of the footer until they
 * are filled in and reviewed (docs/PHASE16.md).
 */
export const metadata: Metadata = {
  title: "Privacy Policy",
  alternates: { canonical: "/privacy" },
  robots: { index: false, follow: true },
};

const SECTIONS: LegalSection[] = [
  {
    id: "data-we-collect",
    title: "1. Data we collect",
    body: (
      <>
        <h3>1.1 You give us</h3>
        <ul>
          <li>Mobile number (India, +91): sign-in by one-time password (OTP); identifies your account. Required.</li>
          <li>Name: shown to organisers and other participants. Required.</li>
          <li>Profile photo: shown on your profile, in league rosters and in the live auction. Required.</li>
          <li>Home state and district: show nearby leagues; shown on your profile. Required.</li>
          <li>Playing role, batting hand, bowling style: player cards and auction listings. Required (bowling style only for bowlers and all-rounders).</li>
          <li>League content you create (name, description, logo, banner, ground, rules, fees, dates, auction settings): only if you organise a league.</li>
          <li>Ground details you register (name and map coordinates): only if you register a ground.</li>
          <li>Payment proof screenshots, for example a UPI receipt: lets a league organiser confirm you paid a league fee. Only if you join a paid league.</li>
          <li>Bids, franchise claims, join requests and follows: created by your actions.</li>
        </ul>
        <h3>1.2 Collected automatically or from your device</h3>
        <ul>
          <li>
            Approximate location, only when you tap &ldquo;Use my location&rdquo; or &ldquo;Show nearest to me&rdquo;. Used to fill your state and
            district, or to sort leagues by distance. We do not track your location in the background.
          </li>
          <li>A push notification token (Firebase Cloud Messaging) for auction and league alerts. You can switch notifications off in your device settings.</li>
          <li>Technical data such as IP address, device and app version, and request logs, kept for security, abuse prevention and debugging.</li>
          <li>Text you type in the ground search, sent to Google Places to return suggestions.</li>
        </ul>
        <p>
          We do not collect your contacts, SMS inbox, call log, microphone recordings, precise background location, government ID, or bank, card or
          UPI credentials. Crichere does not process payments: league fees are paid directly between you and the organiser outside the app, and you
          upload a screenshot as proof. Cover or crop anything in the screenshot you do not want the organiser to see.
        </p>
      </>
    ),
  },
  {
    id: "how-we-use-it",
    title: "2. How we use it",
    body: (
      <>
        <ul>
          <li>Create and secure your account and sign you in.</li>
          <li>Show your profile and player card to the organisers and participants of leagues you join or view.</li>
          <li>Run leagues, the live auction, rosters, results and notifications.</li>
          <li>Let organisers verify league-fee payments.</li>
          <li>Show leagues near you.</li>
          <li>Prevent fraud and abuse, enforce our Terms, comply with law, and fix bugs.</li>
        </ul>
        <p>We do not sell your personal data or use it for third-party advertising. We do not run advertising or analytics SDKs in the app.</p>
      </>
    ),
  },
  {
    id: "who-sees-your-data",
    title: "3. Who sees your data",
    body: (
      <>
        <p>
          Other users: your name, photo, state, district, playing details and league activity (including auction bids and results) are visible to
          organisers and participants of the leagues you join. Public league pages and shared links on crichere.com can show a league&rsquo;s name,
          ground, district, state and live auction results to anyone with the link.
        </p>
        <p>Payment screenshots are visible only to you and the organiser of that league. They are stored privately and served through short-lived links.</p>
        <h3>3.1 Service providers</h3>
        <ul>
          <li>MSG91: sends sign-in OTP SMS (mobile number).</li>
          <li>Google Firebase Cloud Messaging: push notifications (notification token).</li>
          <li>Google Maps and Places: map display and ground search (search text, map coordinates, device and app identifiers as per Google&rsquo;s policy).</li>
          <li>Amazon Web Services (S3): stores photos, logos, banners and payment screenshots.</li>
          <li>Railway: hosts our servers and database.</li>
        </ul>
        <p>These providers may process data outside India. We share only what each needs.</p>
        <p>
          We disclose data if required by law, court order or a government authority, or to protect the rights, safety and security of users or
          Crichere. If Crichere is merged or acquired, data may transfer to the successor, bound by this policy.
        </p>
      </>
    ),
  },
  {
    id: "retention",
    title: "4. Retention",
    body: (
      <ul>
        <li>Account, profile and league data: while your account is active.</li>
        <li>Payment screenshots: [12] months after the league is completed, then deleted.</li>
        <li>Server logs: up to [90] days.</li>
        <li>
          After you delete your account there is a 14-day grace period, then we erase your personal data, normally within a day. We may keep limited
          records longer where law requires. League history other people rely on (auction results, rosters of leagues that already ran) stays, with
          your name replaced by &ldquo;Deleted user&rdquo;.
        </li>
      </ul>
    ),
  },
  {
    id: "your-rights",
    title: "5. Your rights and choices",
    body: (
      <>
        <p>Under the DPDP Act you may:</p>
        <ul>
          <li>Access the personal data we hold about you (most of it is in My Profile).</li>
          <li>Correct or update it (Edit profile in the app).</li>
          <li>
            Delete your account and data: in the app, open My Profile, tap Delete account and type DELETE to confirm. See{" "}
            <Link href="/delete-account">Delete your account</Link> for what happens next and how to ask by email.
          </li>
          <li>Withdraw consent, for example by turning off location or notifications, or deleting your account. This does not affect earlier lawful processing.</li>
          <li>Nominate another person to exercise your rights if you die or cannot act.</li>
          <li>
            Complain to our Grievance Officer (section 9). We reply within [7] days and resolve within 30 days. If unsatisfied, you may approach the
            Data Protection Board of India.
          </li>
        </ul>
      </>
    ),
  },
  {
    id: "security",
    title: "6. Security",
    body: (
      <p>
        Traffic uses HTTPS. Sign-in uses OTP and short-lived tokens. Payment screenshots are private. Access to servers and storage is restricted.
        No system is perfectly secure; if a breach affecting you occurs we will notify you and the authorities as the law requires.
      </p>
    ),
  },
  {
    id: "children",
    title: "7. Children",
    body: (
      <p>
        Crichere is not for children under 18. We do not knowingly collect data from them. If you believe a child has an account, email us and we
        will delete it.
      </p>
    ),
  },
  {
    id: "changes",
    title: "8. Changes",
    body: (
      <p>
        We may update this policy. For material changes we will notify you in the app or by push and update the date above. Continued use after the
        effective date means you accept the change.
      </p>
    ),
  },
  {
    id: "contact",
    title: "9. Contact",
    body: (
      <p>
        Data fiduciary: [Legal name], [registered address], India. Grievance Officer: [Name], <a href="mailto:hello@crichere.com">hello@crichere.com</a>.
      </p>
    ),
  },
];

export default function PrivacyPage() {
  return (
    <LegalArticle
      page="privacy"
      title="Privacy Policy"
      updated="2026-10-11"
      intro="Crichere runs a mobile app and crichere.com for organising cricket leagues, player auctions and franchise teams. This policy explains what personal data we collect, why, who we share it with, and your choices. It follows India's Digital Personal Data Protection Act, 2023 and the Information Technology Act, 2000."
      sections={SECTIONS}
    />
  );
}
