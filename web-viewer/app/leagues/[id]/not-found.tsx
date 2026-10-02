import Link from "next/link";
import { GetAppPopover } from "@/components/ui/GetAppPopover";
import { Icon } from "@/components/ui/Icon";
import { Logo } from "@/components/ui/Logo";
import { StoreBadge } from "@/components/ui/StoreBadge";
import styles from "@/components/LiveAuction.module.css";
import notFound from "./not-found.module.css";

/** Live Auction.dc.html's `notFound` view -- an unknown or removed league id. */
export default function LeagueNotFound() {
  return (
    <div data-surface="dark" className={styles.root}>
      <div aria-hidden="true" className={styles.backdrop}>
        <div className={styles.glowGold} />
        <div className={styles.glowGreen} />
      </div>
      <header className={styles.topBar}>
        <div className={styles.bar}>
          <Logo size={30} fontSize={19} href="/" className={styles.logo} />
          <GetAppPopover />
        </div>
      </header>
      <main className={styles.main}>
        <div className={notFound.body}>
          <span aria-hidden="true" className={notFound.code}>
            404
          </span>
          <span className={notFound.tile}>
            <Icon name="search_off" size={30} />
          </span>
          <h1 className={notFound.title}>We couldn’t find that league</h1>
          <p className={notFound.copy}>
            The link may be mistyped, or the organizer may have removed it. Ask them to share the watch link again — or find leagues near you in the app.
          </p>
          <div className={notFound.actions}>
            <Link href="/" className={notFound.home}>
              <Icon name="home" size={20} />
              Go to Crichere
            </Link>
            <StoreBadge kind="play" />
          </div>
        </div>
      </main>
      <footer className={styles.footer}>
        <div className={styles.footerBar}>
          <span>Public watch link · no account needed to follow along.</span>
          <Link href="/" className={styles.footerLink}>
            Powered by Crichere
          </Link>
        </div>
      </footer>
    </div>
  );
}
