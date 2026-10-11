import type { Metadata } from "next";
import Link from "next/link";
import { LegalArticle, type LegalSection } from "@/components/legal/LegalArticle";

/*
 * The account-deletion URL for the Google Play Data safety form (docs/PHASE16.md). It explains the
 * in-app flow and the email route; there is no web form. Out of search like the other legal pages.
 */
export const metadata: Metadata = {
  title: "Delete your account",
  alternates: { canonical: "/delete-account" },
  robots: { index: false, follow: true },
};

const SECTIONS: LegalSection[] = [
  {
    id: "in-the-app",
    title: "1. Delete in the app",
    body: (
      <ol>
        <li>Open Crichere and go to My Profile.</li>
        <li>Tap Delete account.</li>
        <li>Type DELETE and confirm.</li>
      </ol>
    ),
  },
  {
    id: "what-happens",
    title: "2. What happens next",
    body: (
      <ul>
        <li>You are signed out on every device straight away, and notifications stop.</li>
        <li>For 14 days you can change your mind: sign in again with the same number and the deletion is cancelled.</li>
        <li>After 14 days your account is erased. Signing in with the same number later creates a new, empty account.</li>
      </ul>
    ),
  },
  {
    id: "what-is-deleted",
    title: "3. What is deleted and what is kept",
    body: (
      <>
        <h3>3.1 Deleted</h3>
        <ul>
          <li>Your mobile number, name, photo, state, district and playing details.</li>
          <li>Your payment screenshots, notification tokens, sign-in sessions and follows.</li>
          <li>Your places in leagues that have not run their auction yet.</li>
        </ul>
        <h3>3.2 Kept</h3>
        <ul>
          <li>
            League history other people rely on: auction results and rosters of leagues that already ran, and the franchises in them. Your name
            there becomes &ldquo;Deleted user&rdquo;.
          </li>
          <li>Grounds you registered, without your name.</li>
          <li>
            {/* One flex item: the list item is a flex row (marker + text), so a bare link would split the sentence. */}
            <span>
              Limited records we must keep by law. See the <Link href="/privacy">Privacy Policy</Link> for retention periods.
            </span>
          </li>
        </ul>
      </>
    ),
  },
  {
    id: "before-you-can-delete",
    title: "4. Before you can delete",
    body: (
      <ul>
        <li>No auction you organise, play in or own a franchise in can be running. Wait for it to end.</li>
        <li>
          Each league you organise that is not completed needs a co-organiser. When your account is erased, the earliest co-organiser becomes the
          organiser. Or mark the league completed first.
        </li>
      </ul>
    ),
  },
  {
    id: "without-the-app",
    title: "5. Without the app",
    body: (
      <p>
        Email <a href="mailto:hello@crichere.com?subject=Delete%20my%20account">hello@crichere.com</a> with the subject &ldquo;Delete my
        account&rdquo; and your registered mobile number. We confirm the request with you before acting, and complete it within 30 days.
      </p>
    ),
  },
];

export default function DeleteAccountPage() {
  return (
    <LegalArticle
      page="delete-account"
      title="Delete your Crichere account"
      updated="2026-10-11"
      intro="You can delete your Crichere account and personal data at any time from the app, or by email if you can no longer use the app."
      sections={SECTIONS}
    />
  );
}
