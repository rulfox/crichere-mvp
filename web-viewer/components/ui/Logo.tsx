import Image from "next/image";
import Link from "next/link";
import icon from "@/public/crichere-icon.png";

type LogoProps = {
  /** Icon edge in px -- 34 (landing nav), 30 (footer, auction top bar). */
  size: number;
  /** Wordmark size in px -- 22 / 20 / 19 in the design. */
  fontSize: number;
  /** Render as a home link (nav, top bar) or plain text (footer). */
  href?: string;
  className?: string;
};

/** The Crichere app icon (green square, white "C") beside the Archivo 800 wordmark. */
export function Logo({ size, fontSize, href, className }: LogoProps) {
  const content = (
    <>
      <Image
        src={icon}
        alt=""
        width={size}
        height={size}
        style={{ width: size, height: size, flex: "none", borderRadius: size >= 34 ? 10 : 9, display: "block" }}
        priority
      />
      <span style={{ fontFamily: "var(--font-display)", fontWeight: 800, fontSize, letterSpacing: "-0.02em" }}>Crichere</span>
    </>
  );
  const style = { display: "flex", alignItems: "center", gap: 10 } as const;
  return href ? (
    <Link href={href} aria-label="Crichere home" className={className} style={style}>
      {content}
    </Link>
  ) : (
    <span className={className} style={style}>
      {content}
    </span>
  );
}
