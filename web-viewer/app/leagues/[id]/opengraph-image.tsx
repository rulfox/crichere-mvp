import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { ImageResponse } from "next/og";
import { fetchLeague, fetchLiveNow, type League } from "@/lib/api";
import { LruCache } from "@/lib/lru-cache";
import { cardStatus, locationLine, monogram, nameFontSize, type CardStatus } from "@/lib/og-card";

/**
 * Per-league share card (docs/PHASE13.md), a faithful transcription of the Claude Design file
 * "Crichere League Share Card" -- every size, colour and spacing below comes from its spec, nothing
 * is invented here. Flexbox only, since satori (behind `ImageResponse`) supports nothing else.
 * Any failure falls back to the approved default Crichere card, so a share never shows no image.
 */

export const alt = "Crichere league share card";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// Literal paths (not a helper taking a variable) so Next's file tracing ships exactly these files.
// Satori can't select weights from a variable font (it only renders the default instance), so the
// design's two families ship as the static weights the card actually uses.
const fontsPromise = Promise.all([
  readFile(join(process.cwd(), "assets/fonts/Archivo-ExtraBold.ttf")),
  readFile(join(process.cwd(), "assets/fonts/Archivo-Black.ttf")),
  readFile(join(process.cwd(), "assets/fonts/InstrumentSans-Medium.ttf")),
  readFile(join(process.cwd(), "assets/fonts/InstrumentSans-SemiBold.ttf")),
]);

const svgDataUri = (svg: Buffer) => `data:image/svg+xml;base64,${svg.toString("base64")}`;
const brandPromise = Promise.all([
  readFile(join(process.cwd(), "assets/brand/ic-launcher-full.svg")).then(svgDataUri),
  readFile(join(process.cwd(), "assets/brand/wordmark-white.svg")).then(svgDataUri),
]);

const LOGO_TYPES = new Set(["image/png", "image/jpeg", "image/webp"]);
const LOGO_MAX_BYTES = 2_000_000;
const CACHE_HEADERS = { "Cache-Control": "public, max-age=300, s-maxage=300" };

// ~130 KB per card, so at most ~13 MB (docs/PHASE14.md).
const renderedCards = new LruCache<ArrayBuffer>(100, 60 * 60 * 1000);

/** The league's logo as a data URI, or `null` (-> monogram) when it's missing, slow, too big or not a raster image. */
async function loadLogo(url: string | null): Promise<string | null> {
  if (!url) return null;
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(3000), cache: "no-store" });
    const type = (response.headers.get("content-type") ?? "").split(";")[0].trim().toLowerCase();
    if (!response.ok || !LOGO_TYPES.has(type)) return null;
    const bytes = Buffer.from(await response.arrayBuffer());
    if (bytes.length > LOGO_MAX_BYTES) return null;
    return `data:${type};base64,${bytes.toString("base64")}`;
  } catch {
    return null;
  }
}

async function defaultCard(): Promise<Response> {
  const png = await readFile(join(process.cwd(), "public/og/crichere-default.png"));
  return new Response(new Uint8Array(png), { headers: { "Content-Type": "image/png", ...CACHE_HEADERS } });
}

export default async function Image({ params }: { params: Promise<{ id: string }> }) {
  try {
    const { id } = await params;
    const fetched = await fetchLeague(id);
    if (!fetched) return defaultCard();
    // Stray whitespace from the organizer's input would otherwise count toward the size band.
    const league = { ...fetched, name: fetched.name.trim() };

    const liveNow = await fetchLiveNow();
    const status = cardStatus(liveNow?.leagueId === league.id, league.auctionScheduledAt);
    // Everything the card shows is in the key, so an edit (new logo, rename, going live) is a
    // miss and re-renders at once; repeat crawler hits skip the logo fetch and the render.
    const key = JSON.stringify([league.id, league.name, league.city, league.state, league.logoUrl, status]);
    const cached = renderedCards.get(key);
    if (cached) return new Response(cached, { headers: { "Content-Type": "image/png", ...CACHE_HEADERS } });

    const [logo, fonts, [iconSrc, wordmarkSrc]] = await Promise.all([loadLogo(league.logoUrl), fontsPromise, brandPromise]);
    const png = await renderCard({ league, logo, status, iconSrc, wordmarkSrc }, fonts);
    // A logo that failed to load renders the monogram; don't pin that fallback for the whole TTL.
    if (logo || !league.logoUrl) renderedCards.set(key, png);
    return new Response(png, { headers: { "Content-Type": "image/png", ...CACHE_HEADERS } });
  } catch (error) {
    // Logged so a fallback in production can be diagnosed; the share still gets the default card.
    console.error("[share-card] falling back to the default card:", error);
    return defaultCard();
  }
}

/**
 * ImageResponse renders lazily into its body stream; reading it here is what makes a render failure
 * reject this promise (so the caller falls back) instead of breaking the response mid-stream.
 */
async function renderCard(props: CardProps, [archivo800, archivo900, instrument500, instrument600]: Buffer[]): Promise<ArrayBuffer> {
  const image = new ImageResponse(<Card {...props} />, {
    ...size,
    fonts: [
      { name: "Archivo", data: archivo800, weight: 800, style: "normal" },
      { name: "Archivo", data: archivo900, weight: 900, style: "normal" },
      { name: "Instrument Sans", data: instrument500, weight: 500, style: "normal" },
      { name: "Instrument Sans", data: instrument600, weight: 600, style: "normal" },
    ],
  });
  return image.arrayBuffer();
}

type CardProps = {
  league: League;
  logo: string | null;
  status: CardStatus;
  iconSrc: string;
  wordmarkSrc: string;
};

function Card({ league, logo, status, iconSrc, wordmarkSrc }: CardProps) {
  return (
    <div
      style={{
        position: "relative",
        display: "flex",
        width: 1200,
        height: 630,
        overflow: "hidden",
        backgroundImage: "linear-gradient(135deg, #0b140d 0%, #0b140d 45%, #13211a 100%)",
      }}
    >
      <div style={{ position: "absolute", left: 760, top: 150, width: 760, height: 760, borderRadius: 380, border: "2px solid rgba(215,235,210,0.05)" }} />
      <div style={{ position: "absolute", left: 900, top: 290, width: 480, height: 480, borderRadius: 240, border: "2px solid rgba(215,235,210,0.04)" }} />
      <div style={{ position: "absolute", left: 1090, top: 330, width: 90, height: 260, border: "2px solid rgba(215,235,210,0.05)" }} />

      <div style={{ position: "absolute", left: 100, top: 65, width: 1000, height: 500, display: "flex", flexDirection: "column" }}>
        <div style={{ display: "flex", flex: 1, flexDirection: "row", alignItems: "center", gap: 48 }}>
          {logo ? <LogoTile src={logo} /> : <MonogramTile initials={monogram(league.name)} />}
          <div style={{ display: "flex", flex: 1, flexDirection: "column", alignItems: "flex-start", gap: 20, width: 752, minWidth: 0 }}>
            <Status status={status} />
            <div
              style={{
                display: "block",
                lineClamp: 3,
                width: 752,
                fontFamily: "Archivo",
                fontWeight: 800,
                fontSize: nameFontSize(league.name),
                lineHeight: 1.02,
                letterSpacing: "-0.02em",
                color: "#f5f6f1",
              }}
            >
              {league.name}
            </div>
            <div
              style={{
                display: "block",
                width: 752,
                fontFamily: "Instrument Sans",
                fontWeight: 500,
                fontSize: 32,
                lineHeight: 1.2,
                color: "#b4c0b7",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              {locationLine(league.city, league.state)}
            </div>
          </div>
        </div>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            paddingTop: 24,
            borderTop: "2px solid rgba(215,235,210,0.08)",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
            {/* eslint-disable-next-line @next/next/no-img-element -- satori renders plain <img> only */}
            <img src={iconSrc} alt="" width={44} height={44} style={{ borderRadius: 12 }} />
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={wordmarkSrc} alt="Crichere" width={129} height={32} />
          </div>
          <div style={{ fontFamily: "Instrument Sans", fontWeight: 500, fontSize: 28, color: "#8fa094" }}>crichere.com</div>
        </div>
      </div>
    </div>
  );
}

function LogoTile({ src }: { src: string }) {
  return (
    <div style={{ display: "flex", flex: "none", width: 200, height: 200, padding: 20, borderRadius: 32, background: "#f5f6f1" }}>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={src} alt="" width={160} height={160} style={{ objectFit: "contain" }} />
    </div>
  );
}

function MonogramTile({ initials }: { initials: string }) {
  return (
    <div
      style={{
        display: "flex",
        flex: "none",
        width: 200,
        height: 200,
        alignItems: "center",
        justifyContent: "center",
        borderRadius: 32,
        background: "#1b5e20",
        border: "2px solid rgba(215,235,210,0.16)",
        fontFamily: "Archivo",
        fontWeight: 900,
        fontSize: 88,
        letterSpacing: "-0.02em",
        color: "#d7ebd2",
      }}
    >
      {initials}
    </div>
  );
}

function Status({ status }: { status: CardStatus }) {
  if (status.kind === "live") {
    return (
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 12,
          padding: "10px 22px 10px 18px",
          borderRadius: 999,
          background: "#f2b544",
          color: "#0b140d",
          fontFamily: "Archivo",
          fontWeight: 800,
          fontSize: 28,
          letterSpacing: "0.12em",
          lineHeight: 1,
        }}
      >
        <div style={{ width: 14, height: 14, borderRadius: 7, background: "#0b140d" }} />
        <span>LIVE</span>
      </div>
    );
  }
  if (status.kind === "scheduled") {
    return (
      <div style={{ display: "flex", alignItems: "baseline", gap: 10, fontFamily: "Instrument Sans", fontSize: 30, lineHeight: 1.2 }}>
        <span style={{ fontWeight: 500, color: "#8fa094" }}>Auction on</span>
        <span style={{ fontWeight: 600, color: "#f5f6f1" }}>{status.label}</span>
      </div>
    );
  }
  return null;
}
