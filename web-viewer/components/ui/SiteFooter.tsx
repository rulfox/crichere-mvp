import Link from "next/link";
import { Logo } from "@/components/ui/Logo";
import styles from "./SiteFooter.module.css";

/**
 * Privacy / Terms links stay out of the footer until the pages carry real text (owner decision
 * 2026-10-04): the /privacy and /terms templates exist but hold placeholder copy. Flip this to show them.
 */
export const LEGAL_PAGES_LINKED = false;

type LegalPage = "privacy" | "terms";

/**
 * Design update #5 B4: links only -- Contact · Privacy · Terms. Contact renders only when
 * NEXT_PUBLIC_CONTACT_EMAIL is set (nothing replaces it); no "#" hrefs anywhere. On a legal page the
 * current link is 600 --primary with aria-current="page".
 */
export function SiteFooter({ current }: { current?: LegalPage }) {
  const contact = process.env.NEXT_PUBLIC_CONTACT_EMAIL || null;
  const links: { href: string; label: string; page?: LegalPage }[] = [];
  if (contact) links.push({ href: `mailto:${contact}`, label: "Contact" });
  if (LEGAL_PAGES_LINKED) {
    links.push({ href: "/privacy", label: "Privacy", page: "privacy" });
    links.push({ href: "/terms", label: "Terms", page: "terms" });
  }
  return (
    <footer className={styles.footer}>
      <div className={`${styles.section} ${styles.footerTop}`}>
        <div className={styles.footerBrand}>
          <Logo size={30} fontSize={20} />
          <span className={styles.footerTagline}>Build. Auction. Compete. Made for local cricket across India.</span>
        </div>
        {links.length > 0 && (
          <nav aria-label="Footer" className={styles.footerNav}>
            {links.map((link) =>
              link.page ? (
                <Link
                  key={link.href}
                  href={link.href}
                  className={link.page === current ? styles.current : undefined}
                  aria-current={link.page === current ? "page" : undefined}
                >
                  {link.label}
                </Link>
              ) : (
                <a key={link.href} href={link.href}>
                  {link.label}
                </a>
              ),
            )}
          </nav>
        )}
      </div>
      <div className={`${styles.section} ${styles.copyright}`}>© 2026 Crichere</div>
    </footer>
  );
}
