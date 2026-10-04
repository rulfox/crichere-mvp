import type { ReactNode } from "react";
import { SiteFooter } from "@/components/ui/SiteFooter";
import { LegalToc, type TocItem } from "./LegalToc";
import styles from "./Legal.module.css";

export type LegalSection = TocItem & { body: ReactNode };

const UPDATED_FORMAT = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "long", year: "numeric", timeZone: "UTC" });

/** Design update #5 W8a/W8b: kicker, title, "Last updated", intro, then numbered sections with a TOC. */
export function LegalArticle({
  page,
  title,
  updated,
  intro,
  sections,
}: {
  page: "privacy" | "terms";
  title: string;
  /** ISO date, e.g. "2026-10-04". */
  updated: string;
  intro: ReactNode;
  sections: LegalSection[];
}) {
  const toc = sections.map(({ id, title }) => ({ id, title }));
  return (
    <>
      <main className={styles.main}>
        <div className={styles.grid}>
          <aside className={styles.aside}>
            <LegalToc items={toc} variant="sidebar" />
          </aside>
          <article className={styles.article}>
            <p className={styles.kicker}>Legal</p>
            <h1 className={styles.title}>{title}</h1>
            <p className={styles.updated}>
              Last updated <time dateTime={updated}>{UPDATED_FORMAT.format(new Date(`${updated}T00:00:00Z`))}</time>
            </p>
            <div className={styles.disclosureSlot}>
              <LegalToc items={toc} variant="disclosure" />
            </div>
            <p className={styles.intro}>{intro}</p>
            {sections.map((section) => (
              <section key={section.id} aria-labelledby={section.id}>
                <h2 id={section.id}>{section.title}</h2>
                {section.body}
              </section>
            ))}
          </article>
        </div>
      </main>
      <SiteFooter current={page} />
    </>
  );
}
