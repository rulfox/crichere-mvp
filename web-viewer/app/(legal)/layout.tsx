import Link from "next/link";
import { Icon } from "@/components/ui/Icon";
import styles from "@/components/legal/Legal.module.css";

/** Design update #5 B3: plain light reading page -- wordmark home link, "Back to home", no hero. */
export default function LegalLayout({ children }: LayoutProps<"/">) {
  return (
    <div className={styles.root}>
      <header className={styles.header}>
        <div className={styles.headerInner}>
          <Link href="/" aria-label="Crichere home">
            {/* eslint-disable-next-line @next/next/no-img-element -- static SVG wordmark */}
            <img src="/crichere-wordmark-green.svg" alt="" className={styles.wordmark} />
          </Link>
          <Link href="/" className={styles.back}>
            <Icon name="arrow_back" size={18} />
            <span className={styles.backShort}>Home</span>
            <span className={styles.backLong}>Back to home</span>
          </Link>
        </div>
      </header>
      {children}
    </div>
  );
}
